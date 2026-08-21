package com.example.springai.advisor;

import java.util.ArrayList;
import java.util.List;

import org.springframework.ai.chat.client.ChatClientRequest;
import org.springframework.ai.chat.client.ChatClientResponse;
import org.springframework.ai.chat.client.advisor.api.CallAdvisor;
import org.springframework.ai.chat.client.advisor.api.CallAdvisorChain;
import org.springframework.ai.chat.client.advisor.api.StreamAdvisor;
import org.springframework.ai.chat.client.advisor.api.StreamAdvisorChain;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.core.Ordered;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;

@Component
public class GuardrailAdvisor implements CallAdvisor, StreamAdvisor {

    private static final Resource GUARDRAIL_PROMPT = new ClassPathResource("prompts/guardrail-system.st");

    @Override
    public ChatClientResponse adviseCall(ChatClientRequest chatClientRequest, CallAdvisorChain callAdvisorChain) {
        return callAdvisorChain.nextCall(applyGuardrail(chatClientRequest));
    }

    @Override
    public Flux<ChatClientResponse> adviseStream(
            ChatClientRequest chatClientRequest,
            StreamAdvisorChain streamAdvisorChain) {
        return streamAdvisorChain.nextStream(applyGuardrail(chatClientRequest));
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
