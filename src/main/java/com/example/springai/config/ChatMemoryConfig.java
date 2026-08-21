package com.example.springai.config;

import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.chat.memory.ChatMemoryRepository;
import org.springframework.ai.chat.memory.MessageWindowChatMemory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 멀티턴 대화 메모리.
 *
 * <p>저장소는 자동 구성된 {@code JdbcChatMemoryRepository}를 쓴다. 대화가 PostgreSQL의
 * {@code SPRING_AI_CHAT_MEMORY} 테이블에 남으므로 애플리케이션을 재시작해도 맥락이 유지된다.
 *
 * <p>대화가 길어지면 토큰이 무한정 늘어나므로 최근 {@code app.chat.memory.max-messages}개만 남기는
 * 윈도우 방식을 쓴다.
 */
@Configuration
public class ChatMemoryConfig {

    @Bean
    public ChatMemory chatMemory(ChatMemoryRepository chatMemoryRepository,
            @Value("${app.chat.memory.max-messages}") int maxMessages) {
        return MessageWindowChatMemory.builder()
                .chatMemoryRepository(chatMemoryRepository)
                .maxMessages(maxMessages)
                .build();
    }
}
