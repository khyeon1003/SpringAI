package com.example.springai.service;

import java.util.List;

import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

@Component
public class Retriever {

    private final VectorStore vectorStore;
    private final int topK;

    public Retriever(VectorStore vectorStore, @Value("${app.rag.top-k}") int topK) {
        if (topK <= 0) {
            throw new IllegalArgumentException("app.rag.top-k must be greater than 0");
        }
        this.vectorStore = vectorStore;
        this.topK = topK;
    }

    public List<Document> retrieveChunks(String query) {
        if (!StringUtils.hasText(query)) {
            throw new IllegalArgumentException("query must not be blank");
        }

        SearchRequest searchRequest = SearchRequest.builder()
                .query(query)
                .topK(topK)
                .build();

        return vectorStore.similaritySearch(searchRequest);
    }
}
