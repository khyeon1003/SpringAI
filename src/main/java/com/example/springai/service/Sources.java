package com.example.springai.service;

import java.util.List;
import java.util.Map;
import java.util.Objects;

import com.example.springai.dto.Source;
import org.springframework.ai.document.Document;

/**
 * 검색된 청크의 메타데이터에서 출처를 뽑는다.
 *
 * <p>Advisor가 근거를 넣어 주더라도 출처는 우리가 꺼내 붙여야 한다.
 */
public final class Sources {

    private Sources() {
    }

    /** 같은 규정이 여러 번 검색되어도 출처는 한 번만 남긴다. */
    public static List<Source> from(List<Document> chunks) {
        if (chunks == null || chunks.isEmpty()) {
            return List.of();
        }

        return chunks.stream()
                .map(Sources::toSource)
                .distinct()
                .toList();
    }

    /** 모델이 답변에서 근거를 지목할 수 있도록 청크마다 출처를 붙인다. */
    public static String label(Document chunk, int index) {
        Source source = toSource(chunk);
        return "[출처 %d] %s · %s · %s".formatted(
                index, source.document(), source.section(), source.heading());
    }

    private static Source toSource(Document chunk) {
        Map<String, Object> metadata = chunk.getMetadata();
        return new Source(
                text(metadata.get("source")),
                text(metadata.get("section")),
                text(metadata.get("heading")),
                text(metadata.get("version")));
    }

    private static String text(Object value) {
        return Objects.toString(value, "");
    }
}
