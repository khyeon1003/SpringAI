package com.example.springai.service;

import java.util.List;

import com.example.springai.dto.ChatAction;
import com.example.springai.dto.ChatResponse;
import org.springframework.ai.document.Document;
import reactor.core.publisher.Flux;

@org.springframework.stereotype.Service
public class Service {

    private final Retriever retriever;
    private final AnswerGenerator answerGenerator;

    public Service(Retriever retriever, AnswerGenerator answerGenerator) {
        this.retriever = retriever;
        this.answerGenerator = answerGenerator;
    }

    public ChatResponse answer(Long userId, String query) {
        var retrievedChunks = retriever.retrieveChunks(query);
        String answer = answerGenerator.generate(query, retrievedChunks, userId);
        List<String> contexts = retrievedChunks.stream()
                .map(Document::getText)
                .toList();
        return new ChatResponse(ChatAction.ANSWER, answer, contexts);
    }

    public Flux<String> streamAnswer(Long userId, String query) {
        var retrievedChunks = retriever.retrieveChunks(query);
        return answerGenerator.stream(query, retrievedChunks, userId);
    }
}
