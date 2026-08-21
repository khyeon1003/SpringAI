package com.example.springai.config;

import com.example.springai.advisor.ModelCallLoggingAdvisor;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableConfigurationProperties(ModelConfig.class)
public class ChatClientConfig {

    @Bean
    public ChatClient chatClient(
            ChatModel chatModel,
            ModelConfig modelConfig,
            ModelCallLoggingAdvisor modelCallLoggingAdvisor) {
        return ChatClient.builder(chatModel)
                .defaultOptions(modelConfig.toChatOptionsBuilder())
                .defaultAdvisors(modelCallLoggingAdvisor)
                .build();
    }
}
