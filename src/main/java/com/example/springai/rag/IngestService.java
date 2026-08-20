package com.example.springai.rag;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Phase 2 - 학칙 문서를 청크로 나눠 임베딩하고 벡터 스토어에 저장한다.
 *
 * <p>청킹은 {@link MarkdownHeadingSplitter}가 담당한다. 토큰 수 기준으로 자르면 제목과 본문이
 * 갈라져 근거를 찾을 수 없게 되므로, 문서가 `##`로 표시해 둔 규정 단위를 그대로 청크로 삼는다.
 */
@Service
public class IngestService {

    private static final Logger log = LoggerFactory.getLogger(IngestService.class);
    private static final String DOCS_PATTERN = "classpath:/docs/*.md";

    private final VectorStore vectorStore;
    private final MarkdownHeadingSplitter splitter = new MarkdownHeadingSplitter();

    public IngestService(VectorStore vectorStore) {
        this.vectorStore = vectorStore;
    }

    /** classpath:/docs 아래의 모든 마크다운 문서를 인제스트한다. */
    public List<IngestResult> ingestAll() {
        Resource[] resources;
        try {
            resources = new PathMatchingResourcePatternResolver().getResources(DOCS_PATTERN);
        }
        catch (IOException e) {
            throw new UncheckedIOException("문서를 찾지 못했습니다: " + DOCS_PATTERN, e);
        }

        List<IngestResult> results = new ArrayList<>();
        Arrays.stream(resources)
                .sorted(Comparator.comparing(Resource::getFilename))
                .forEach(resource -> results.add(ingest(resource)));
        return results;
    }

    /**
     * 문서 하나를 인제스트한다.
     *
     * <p>같은 문서를 다시 넣으면 청크가 중복해서 쌓이고 검색 결과가 같은 문장으로 도배된다.
     * 그래서 추가하기 전에 같은 source의 기존 청크를 먼저 지운다.
     */
    public IngestResult ingest(Resource resource) {
        String source = resource.getFilename();

        vectorStore.delete("source == '" + source + "'");           // 1. 재색인 대비

        String markdown = read(resource);
        List<Document> chunks = splitter.split(markdown, source);   // 2. `##` 단위 청킹

        String version = LocalDate.now().toString();
        List<Document> enriched = chunks.stream().map(chunk -> {    // 3. 메타데이터
            Map<String, Object> metadata = new HashMap<>(chunk.getMetadata());
            metadata.put("version", version);
            return new Document(chunk.getText(), metadata);
        }).toList();

        vectorStore.add(enriched);                                  // 4. 임베딩 + 저장

        log.info("인제스트 완료 - {} : 청크 {}개", source, enriched.size());
        return new IngestResult(source, enriched.size());
    }

    private String read(Resource resource) {
        try (var in = resource.getInputStream()) {
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
        catch (IOException e) {
            throw new UncheckedIOException("문서를 읽지 못했습니다: " + resource.getFilename(), e);
        }
    }

    public record IngestResult(String source, int chunkCount) {
    }
}
