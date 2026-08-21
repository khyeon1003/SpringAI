package com.example.springai.service;

import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

@Component
public class Retriever {

    private static final Logger log = LoggerFactory.getLogger(Retriever.class);

    private final VectorStore vectorStore;
    private final int topK;
    private final double similarityThreshold;

    public Retriever(VectorStore vectorStore,
            @Value("${app.rag.top-k}") int topK,
            @Value("${app.rag.similarity-threshold}") double similarityThreshold) {
        if (topK <= 0) {
            throw new IllegalArgumentException("app.rag.top-k must be greater than 0");
        }
        if (similarityThreshold < 0 || similarityThreshold > 1) {
            throw new IllegalArgumentException("app.rag.similarity-threshold must be between 0 and 1");
        }
        this.vectorStore = vectorStore;
        this.topK = topK;
        this.similarityThreshold = similarityThreshold;
    }

    public List<Document> retrieveChunks(String query) {
        if (!StringUtils.hasText(query)) {
            throw new IllegalArgumentException("query must not be blank");
        }

        SearchRequest searchRequest = SearchRequest.builder()
                .query(query)
                .topK(topK)
                .similarityThreshold(similarityThreshold)
                .build();

        List<Document> chunks = vectorStore.similaritySearch(searchRequest);
        log.info("검색 - 검색어=[{}] 임계값={} 결과={}건", query, similarityThreshold, chunks.size());
        return chunks;
    }
}
