package com.example.springai.service;

import java.util.List;

import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

@Component
public class Retriever {

    private static final int DEFAULT_TOP_K = 4;

    private final VectorStore vectorStore;

    public Retriever(VectorStore vectorStore) {
        this.vectorStore = vectorStore;
    }

    public List<Document> retrieve(String query) {
        return retrieve(query, DEFAULT_TOP_K);
    }

    public List<Document> retrieve(String query, int topK) {
        if (!StringUtils.hasText(query)) {
            throw new IllegalArgumentException("query must not be blank");
        }

        int limit = topK <= 0 ? DEFAULT_TOP_K : topK;
        SearchRequest searchRequest = SearchRequest.builder()
                .query(query)
                .topK(limit)
                .build();

        return vectorStore.similaritySearch(searchRequest);
    }
}
