package com.example.springai.service;

import java.util.List;

import com.example.springai.advisor.GuardrailAdvisor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.chat.messages.Message;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

/**
 * 파이프라인 가장 앞단. 사용자 질문을 검색에 적합한 형태로 재작성한다.
 *
 * <p>이 호출에만 {@link GuardrailAdvisor}를 붙인다. Advisor가 LLM 호출 직전에 가드레일 프롬프트를
 * 주입하므로, 차단 대상이면 응답이 {@code [[BLOCK]]}으로 시작하고 Advisor가
 * {@link com.example.springai.advisor.GuardrailBlockedException}을 던진다. 그 경우 검색도 Tool 호출도
 * 일어나지 않는다.
 *
 * <p>대화 이력을 함께 넘겨 "그거", "그럼 그건" 같은 지시어를 구체적인 규정 주제로 되돌린다.
 * 이력은 프롬프트 파라미터로만 넣는다. 메모리 Advisor를 여기에 붙이면 재작성 결과가 대화 기록에
 * 남아 다음 턴의 맥락을 오염시킨다.
 */
@Component
public class QueryRewriter {

    private static final Logger log = LoggerFactory.getLogger(QueryRewriter.class);

    private static final Resource SYSTEM_PROMPT = new ClassPathResource("prompts/rewrite-system.st");
    private static final Resource USER_PROMPT = new ClassPathResource("prompts/rewrite-user.st");
    private static final String NO_HISTORY = "(no previous conversation)";
    private static final int HISTORY_TURNS = 6;

    private final ChatClient chatClient;
    private final GuardrailAdvisor guardrailAdvisor;
    private final ChatMemory chatMemory;

    public QueryRewriter(ChatClient chatClient, GuardrailAdvisor guardrailAdvisor, ChatMemory chatMemory) {
        this.chatClient = chatClient;
        this.guardrailAdvisor = guardrailAdvisor;
        this.chatMemory = chatMemory;
    }

    /**
     * @param conversationId 대화 식별자. {@code null}이면 이력 없이 재작성한다.
     * @return 검색에 사용할 재작성된 질문
     * @throws com.example.springai.advisor.GuardrailBlockedException 가드레일이 요청을 차단한 경우
     */
    public String rewrite(String question, String conversationId) {
        if (!StringUtils.hasText(question)) {
            throw new IllegalArgumentException("question must not be blank");
        }

        String history = recentHistory(conversationId);

        String rewritten = chatClient.prompt()
                .advisors(guardrailAdvisor)
                .system(SYSTEM_PROMPT)
                .user(user -> user.text(USER_PROMPT)
                        .param("history", history)
                        .param("question", question))
                .call()
                .content();

        if (!StringUtils.hasText(rewritten)) {
            // 재작성에 실패해도 검색은 진행할 수 있어야 하므로 원문을 그대로 쓴다.
            log.warn("질문 재작성 결과가 비어 있어 원문으로 검색한다 - question={}", question);
            return question;
        }

        String searchQuery = rewritten.strip();
        log.info("질문 재작성 - 원문=[{}] 검색어=[{}] 이력={}",
                question, searchQuery, NO_HISTORY.equals(history) ? "없음" : "있음");
        return searchQuery;
    }

    private String recentHistory(String conversationId) {
        if (conversationId == null) {
            return NO_HISTORY;
        }

        List<Message> messages = chatMemory.get(conversationId);
        if (messages == null || messages.isEmpty()) {
            return NO_HISTORY;
        }

        List<Message> recent = messages.size() <= HISTORY_TURNS
                ? messages
                : messages.subList(messages.size() - HISTORY_TURNS, messages.size());

        StringBuilder history = new StringBuilder();
        for (Message message : recent) {
            history.append(message.getMessageType().getValue()).append(": ")
                    .append(message.getText().replace('\n', ' ')).append('\n');
        }
        return history.toString().strip();
    }
}
