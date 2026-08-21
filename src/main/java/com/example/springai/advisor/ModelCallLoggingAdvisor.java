package com.example.springai.advisor;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClientRequest;
import org.springframework.ai.chat.client.ChatClientResponse;
import org.springframework.ai.chat.client.advisor.api.CallAdvisor;
import org.springframework.ai.chat.client.advisor.api.CallAdvisorChain;
import org.springframework.core.Ordered;
import org.springframework.stereotype.Component;

@Component
public class ModelCallLoggingAdvisor implements CallAdvisor {

    private static final Logger log = LoggerFactory.getLogger(ModelCallLoggingAdvisor.class);

    @Override
    public ChatClientResponse adviseCall(
            ChatClientRequest chatClientRequest,
            CallAdvisorChain callAdvisorChain) {
        log.info("chat_model_call model={}", chatClientRequest.prompt().getOptions().getModel());
        return callAdvisorChain.nextCall(chatClientRequest);
    }

    @Override
    public String getName() {
        return "modelCallLoggingAdvisor";
    }

    @Override
    public int getOrder() {
        return Ordered.HIGHEST_PRECEDENCE;
    }
}
