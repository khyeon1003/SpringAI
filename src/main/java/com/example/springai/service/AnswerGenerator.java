package com.example.springai.service;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import com.example.springai.metrics.TokenUsageMetrics;
import com.example.springai.tool.UserTool;
import org.springframework.ai.chat.client.ChatClient;
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
    private final TokenUsageMetrics tokenUsageMetrics;

    public AnswerGenerator(ChatClient chatClient, UserTool userTool, TokenUsageMetrics tokenUsageMetrics) {
        this.chatClient = chatClient;
        this.userTool = userTool;
        this.tokenUsageMetrics = tokenUsageMetrics;
    }

    public String generate(String query, List<Document> retrievedChunks, Long userId) {
        if (!StringUtils.hasText(query)) {
            throw new IllegalArgumentException("query must not be blank");
        }

        String context = toContext(retrievedChunks);

        var chatResponse = chatClient.prompt()
                .system(SYSTEM_PROMPT)
                .user(user -> user.text(USER_PROMPT)
                        .param("context", context)
                        .param("query", query))
                .tools(userTool)
                .toolContext(toToolContext(userId))
                .call()
                .chatResponse();

        if (chatResponse == null) {
            throw new IllegalStateException("Chat model returned no response.");
        }
        tokenUsageMetrics.record(chatResponse.getMetadata().getUsage());
        return chatResponse.getResult().getOutput().getText();
    }

    public Flux<String> stream(String query, List<Document> retrievedChunks, Long userId) {
        if (!StringUtils.hasText(query)) {
            return Flux.error(new IllegalArgumentException("query must not be blank"));
        }

        String context = toContext(retrievedChunks);
        return chatClient.prompt()
                .system(SYSTEM_PROMPT)
                .user(user -> user.text(USER_PROMPT)
                        .param("context", context)
                        .param("query", query))
                .tools(userTool)
                .toolContext(toToolContext(userId))
                .stream()
                .content();
    }

    private Map<String, Object> toToolContext(Long userId) {
        if (userId == null) {
            return Map.of();
        }

        return Map.of(UserTool.USER_ID_CONTEXT_KEY, userId);
    }

    private String toContext(List<Document> retrievedChunks) {
        if (retrievedChunks == null || retrievedChunks.isEmpty()) {
            return "";
        }

        return retrievedChunks.stream()
                .map(Document::getText)
                .filter(StringUtils::hasText)
                .collect(Collectors.joining("\n\n---\n\n"));
    }
}
