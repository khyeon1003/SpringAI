package com.example.springai.service;

import java.util.List;

import com.example.springai.advisor.GuardrailBlockedException;
import com.example.springai.dto.ChatAction;
import com.example.springai.dto.ChatResponse;
import org.springframework.ai.document.Document;

@org.springframework.stereotype.Service
public class Service {

    private final Retriever retriever;
    private final AnswerGenerator answerGenerator;

    public Service(Retriever retriever, AnswerGenerator answerGenerator) {
        this.retriever = retriever;
        this.answerGenerator = answerGenerator;
    }

    public ChatResponse answer(Long userId, String query) {
        var documents = retriever.retrieve(query);
        try {
            String answer = answerGenerator.generate(query, documents, userId);
            List<String> contexts = documents.stream()
                    .map(Document::getText)
                    .toList();
            return new ChatResponse(ChatAction.ANSWER, answer, contexts);
        }
        catch (GuardrailBlockedException exception) {
            return new ChatResponse(ChatAction.BLOCK, exception.getMessage(), List.of());
        }
    }
}
