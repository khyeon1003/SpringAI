package com.example.springai.service;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;

import com.example.springai.metrics.TokenUsageMetrics;
import com.example.springai.tool.UserTool;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.document.Document;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import reactor.core.publisher.Flux;

@Component
public class AnswerGenerator {

    private static final Resource SYSTEM_PROMPT = new ClassPathResource("prompts/rag-system.st");
    private static final Resource USER_PROMPT = new ClassPathResource("prompts/rag-user.st");

    private final ChatClient chatClient;
    private final UserTool userTool;
    private final ChatMemory chatMemory;
    private final TokenUsageMetrics tokenUsageMetrics;

    public AnswerGenerator(ChatClient chatClient, UserTool userTool, ChatMemory chatMemory,
            TokenUsageMetrics tokenUsageMetrics) {
        this.chatClient = chatClient;
        this.userTool = userTool;
        this.chatMemory = chatMemory;
        this.tokenUsageMetrics = tokenUsageMetrics;
    }

    /**
     * @param query          사용자가 실제로 입력한 원문. 재작성된 검색어가 아니다.
     *                       개인화 단서와 지시어를 보존해야 Tool 호출과 맥락 유지가 정상 동작한다.
     * @param conversationId 대화 식별자. {@code null}이면 이력 없이 이번 질문만으로 답한다.
     */
    public GeneratedAnswer generate(String query, List<Document> retrievedChunks, Long userId,
            String conversationId) {
        if (!StringUtils.hasText(query)) {
            throw new IllegalArgumentException("query must not be blank");
        }

        List<String> toolCalls = new CopyOnWriteArrayList<>();

        var chatResponse = promptFor(query, retrievedChunks, userId, conversationId, toolCalls)
                .call()
                .chatResponse();

        if (chatResponse == null) {
            throw new IllegalStateException("Chat model returned no response.");
        }

        tokenUsageMetrics.record(chatResponse.getMetadata().getUsage());

        String answer = chatResponse.getResult().getOutput().getText();
        remember(conversationId, query, answer);
        return new GeneratedAnswer(answer, new ArrayList<>(toolCalls));
    }

    /**
     * 답변과 그 과정에서 호출된 도구.
     *
     * <p>도구를 실제로 썼는지는 응답 본문만 봐서는 알 수 없다. 검증할 수 있도록 함께 돌려준다.
     */
    public record GeneratedAnswer(String text, List<String> toolCalls) {
    }

    /**
     * 토큰 단위로 답변을 흘려보낸다. 동기 응답과 같은 프롬프트, 같은 도구, 같은 대화 이력을 쓴다.
     * 스트리밍은 전달 방식일 뿐이므로 파이프라인이 갈라지면 안 된다.
     */
    public Flux<String> stream(String query, List<Document> retrievedChunks, Long userId, String conversationId) {
        if (!StringUtils.hasText(query)) {
            return Flux.error(new IllegalArgumentException("query must not be blank"));
        }

        StringBuilder collected = new StringBuilder();
        return promptFor(query, retrievedChunks, userId, conversationId, new CopyOnWriteArrayList<>())
                .stream()
                .chatResponse()
                .doOnNext(response -> {
                    if (response.getMetadata() != null) {
                        tokenUsageMetrics.record(response.getMetadata().getUsage());
                    }
                })
                .mapNotNull(response -> response.getResult() == null
                        ? null
                        : response.getResult().getOutput().getText())
                .filter(StringUtils::hasText)
                .doOnNext(collected::append)
                // 스트림이 끝난 뒤에야 답변 전체가 완성되므로 여기서 기록한다.
                .doOnComplete(() -> remember(conversationId, query, collected.toString()));
    }

    private ChatClient.ChatClientRequestSpec promptFor(String query, List<Document> retrievedChunks,
            Long userId, String conversationId, List<String> toolCalls) {
        String context = toContext(retrievedChunks);

        var prompt = chatClient.prompt()
                .system(SYSTEM_PROMPT)
                .messages(history(conversationId))
                .user(user -> user.text(USER_PROMPT)
                        .param("context", context)
                        .param("query", query));

        // 현재 사용자를 특정할 수 없으면 개인 학사정보 도구를 아예 노출하지 않는다.
        // 빈 ToolContext 로 도구만 붙여 두면 모델이 호출했을 때 요청 자체가 실패한다.
        if (userId != null) {
            prompt = prompt.tools(userTool).toolContext(toToolContext(userId, toolCalls));
        }
        return prompt;
    }

    private List<Message> history(String conversationId) {
        if (conversationId == null) {
            return List.of();
        }
        List<Message> messages = chatMemory.get(conversationId);
        return messages == null ? List.of() : messages;
    }

    /**
     * 이번 턴을 대화 기록에 남긴다.
     *
     * <p>{@code MessageChatMemoryAdvisor}를 쓰지 않고 직접 저장하는 이유가 있다. Advisor는 렌더링된
     * 사용자 메시지를 그대로 기록하는데, 우리 사용자 프롬프트에는 검색된 청크 전문이 들어 있다.
     * 그대로 두면 턴마다 문서 본문이 대화 기록에 쌓여 창을 잡아먹고, 다음 턴에 다시 전송된다.
     * 여기서는 질문과 답변만 남겨 기록을 깨끗하게 유지한다.
     */
    private void remember(String conversationId, String query, String answer) {
        if (conversationId == null || !StringUtils.hasText(answer)) {
            return;
        }
        chatMemory.add(conversationId, List.of(new UserMessage(query), new AssistantMessage(answer)));
    }

    private Map<String, Object> toToolContext(Long userId, List<String> toolCalls) {
        return Map.of(
                UserTool.USER_ID_CONTEXT_KEY, userId,
                UserTool.TOOL_CALLS_CONTEXT_KEY, toolCalls);
    }

    /** 청크마다 출처 라벨을 붙여, 모델이 어떤 규정을 근거로 삼았는지 답변에서 지목할 수 있게 한다. */
    private String toContext(List<Document> retrievedChunks) {
        if (retrievedChunks == null || retrievedChunks.isEmpty()) {
            return "";
        }

        StringBuilder context = new StringBuilder();
        int index = 0;
        for (Document chunk : retrievedChunks) {
            if (!StringUtils.hasText(chunk.getText())) {
                continue;
            }
            if (index > 0) {
                context.append("\n\n---\n\n");
            }
            context.append(Sources.label(chunk, ++index)).append('\n').append(chunk.getText());
        }
        return context.toString();
    }
}
