package com.example.springai.advisor;

import java.util.ArrayList;
import java.util.List;

import org.springframework.ai.chat.client.ChatClientRequest;
import org.springframework.ai.chat.client.ChatClientResponse;
import org.springframework.ai.chat.client.advisor.api.CallAdvisor;
import org.springframework.ai.chat.client.advisor.api.CallAdvisorChain;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.core.Ordered;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Component;

@Component
public class GuardrailAdvisor implements CallAdvisor {

    private static final Resource GUARDRAIL_PROMPT = new ClassPathResource("prompts/guardrail-system.st");
    private static final String BLOCK_PREFIX = "[[BLOCK]]";

    @Override
    public ChatClientResponse adviseCall(ChatClientRequest chatClientRequest, CallAdvisorChain callAdvisorChain) {
        ChatClientResponse response = callAdvisorChain.nextCall(applyGuardrail(chatClientRequest));
        String content = response.chatResponse().getResult().getOutput().getText();
        if (content != null && content.stripLeading().startsWith(BLOCK_PREFIX)) {
            String message = content.stripLeading().substring(BLOCK_PREFIX.length()).strip();
            throw new GuardrailBlockedException(
                    message.isBlank() ? "요청을 처리할 수 없습니다." : message);
        }
        return response;
    }

    @Override
    public String getName() {
        return "guardrailAdvisor";
    }

    @Override
    public int getOrder() {
        return Ordered.HIGHEST_PRECEDENCE;
    }

    private ChatClientRequest applyGuardrail(ChatClientRequest chatClientRequest) {
        Prompt prompt = chatClientRequest.prompt();
        List<Message> messages = new ArrayList<>();
        messages.add(new SystemMessage(GUARDRAIL_PROMPT));
        messages.addAll(prompt.getInstructions());

        Prompt guardedPrompt = new Prompt(messages, prompt.getOptions());

        return chatClientRequest.mutate()
                .prompt(guardedPrompt)
                .build();
    }
}
