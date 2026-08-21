package com.example.springai.dto;

import java.util.List;

/**
 * @param action    답변했는지 차단했는지
 * @param route     무엇을 근거로 답했는지
 * @param answer    답변 본문, 또는 차단 사유
 * @param toolUsed  개인 학적 정보 도구를 호출했는지
 * @param toolCalls 호출한 도구 이름. 호출 순서대로 담긴다.
 * @param contexts  답변 근거로 사용한 청크 본문. RAGAS 평가가 이 값을 사용한다.
 * @param sources   각 청크의 출처. {@code contexts} 와 순서가 대응한다.
 */
public record ChatResponse(
        ChatAction action,
        ChatRoute route,
        String answer,
        boolean toolUsed,
        List<String> toolCalls,
        List<String> contexts,
        List<Source> sources) {

    public static ChatResponse blocked(String reason) {
        return new ChatResponse(ChatAction.BLOCK, ChatRoute.BLOCK, reason,
                false, List.of(), List.of(), List.of());
    }

    public static ChatResponse answered(String answer, List<String> toolCalls,
            List<String> contexts, List<Source> sources) {
        return new ChatResponse(ChatAction.ANSWER, route(toolCalls, contexts), answer,
                !toolCalls.isEmpty(), List.copyOf(toolCalls), contexts, sources);
    }

    /** 근거 본문을 뺀 응답. 사람이 눈으로 확인할 때 청크 전문이 화면을 덮는 것을 막는다. */
    public ChatResponse withoutContexts() {
        return new ChatResponse(action, route, answer, toolUsed, toolCalls, List.of(), sources);
    }

    private static ChatRoute route(List<String> toolCalls, List<String> contexts) {
        boolean hasTool = !toolCalls.isEmpty();
        boolean hasContext = !contexts.isEmpty();

        if (hasContext && hasTool) {
            return ChatRoute.RAG_WITH_TOOL;
        }
        if (hasContext) {
            return ChatRoute.RAG_ONLY;
        }
        return hasTool ? ChatRoute.TOOL_ONLY : ChatRoute.NO_EVIDENCE;
    }
}
