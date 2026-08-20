package com.example.springai.rag;

import org.junit.jupiter.api.Test;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.List;

/**
 * 학칙 문서를 실제로 임베딩해 pgvector에 적재한다.
 * OpenAI API 키와 PostgreSQL 접속이 필요하므로, 인제스트가 필요할 때만 명시적으로 실행한다.
 *
 * <pre>./gradlew test --tests "*IngestRunnerTest*"</pre>
 */
@SpringBootTest
class IngestRunnerTest {

    @Autowired
    private IngestService ingestService;

    @Autowired
    private VectorStore vectorStore;

    @Test
    void ingestAllDocuments() {
        List<IngestService.IngestResult> results = ingestService.ingestAll();

        int total = 0;
        System.out.println("\n=== 인제스트 결과 ===");
        for (IngestService.IngestResult result : results) {
            System.out.printf("%-20s 청크 %d개%n", result.source(), result.chunkCount());
            total += result.chunkCount();
        }
        System.out.println("합계 " + total + "개");

        System.out.println("\n=== 검색 확인: \"육아휴학 신청할 때 내는 서류\" ===");
        vectorStore.similaritySearch(SearchRequest.builder()
                        .query("육아휴학 신청할 때 내는 서류")
                        .topK(3)
                        .build())
                .forEach(doc -> System.out.printf("  [%.3f] %s / %s%n",
                        doc.getScore(),
                        doc.getMetadata().get("source"),
                        doc.getMetadata().get("heading")));
    }
}
