package com.example.springai.config;

import org.springframework.ai.chat.prompt.ChatOptions;
import org.springframework.ai.openai.OpenAiChatOptions;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.util.StringUtils;

@ConfigurationProperties(prefix = "spring.ai.openai.chat.options")
public class ModelConfig {

    private String model;
    private Double temperature;
    private Integer maxTokens;

    public ChatOptions.Builder<?> toChatOptionsBuilder() {
        OpenAiChatOptions.Builder builder = OpenAiChatOptions.builder();

        if (StringUtils.hasText(model)) {
            builder.model(model);
        }

        if (temperature != null) {
            builder.temperature(temperature);
        }

        if (maxTokens != null) {
            builder.maxTokens(maxTokens);
        }

        return builder;
    }

    public String getModel() {
        return model;
    }

    public void setModel(String model) {
        this.model = model;
    }

    public Double getTemperature() {
        return temperature;
    }

    public void setTemperature(Double temperature) {
        this.temperature = temperature;
    }

    public Integer getMaxTokens() {
        return maxTokens;
    }

    public void setMaxTokens(Integer maxTokens) {
        this.maxTokens = maxTokens;
    }
}
