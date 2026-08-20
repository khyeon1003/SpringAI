package com.example.springai.service;

@org.springframework.stereotype.Service
public class Service {

    private final Retriever retriever;
    private final AnswerGenerator answerGenerator;

    public Service(Retriever retriever, AnswerGenerator answerGenerator) {
        this.retriever = retriever;
        this.answerGenerator = answerGenerator;
    }

    public String answer(Long userId, String query, Integer topK) {
        var documents = topK == null ? retriever.retrieve(query) : retriever.retrieve(query, topK);

        return answerGenerator.generate(query, documents, userId);
    }
}
