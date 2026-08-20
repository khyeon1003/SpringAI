package com.example.springai.rag;

import com.knuddels.jtokkit.Encodings;
import com.knuddels.jtokkit.api.Encoding;
import com.knuddels.jtokkit.api.EncodingType;
import org.junit.jupiter.api.Test;
import org.springframework.ai.document.Document;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

/**
 * Phase 2 - `##` 헤딩 기준 청킹 결과를 800토큰 덤프와 같은 형식으로 남긴다.
 * 두 전략을 같은 잣대로 비교하기 위한 것이며, 여기서도 DB에는 저장하지 않는다.
 */
class HeadingChunkDumpTest {

    private static final Path DOCS_DIR = Path.of("src/main/resources/docs");
    private static final Path OUT_DIR = Path.of("chunk-dump/heading");

    private final Encoding encoding = Encodings.newDefaultEncodingRegistry()
            .getEncoding(EncodingType.CL100K_BASE);

    @Test
    void dumpHeadingChunks() throws IOException {
        Files.createDirectories(OUT_DIR);

        MarkdownHeadingSplitter splitter = new MarkdownHeadingSplitter();

        List<Path> files;
        try (Stream<Path> paths = Files.list(DOCS_DIR)) {
            files = paths.filter(p -> p.toString().endsWith(".md")).sorted().toList();
        }

        StringBuilder summary = new StringBuilder();
        summary.append("# `##` 헤딩 청킹 결과 요약\n\n")
                .append("설정: `MarkdownHeadingSplitter` - `##` 단위 분할, 상위 `#` 제목 경로 주입, 병합 없음\n\n")
                .append("| 문서 | 청크 | 토큰 | 글자 | 헤딩 | 헤딩 없이 시작 | 헤딩만 남고 끝 |\n")
                .append("|---|---:|---:|---:|---|:---:|:---:|\n");

        int totalChunks = 0;
        int orphanStart = 0;
        int loneHeadingEnd = 0;
        int minTokens = Integer.MAX_VALUE;
        int maxTokens = 0;

        for (Path file : files) {
            String source = file.getFileName().toString();
            String markdown = Files.readString(file, StandardCharsets.UTF_8);
            List<Document> chunks = splitter.split(markdown, source);
            totalChunks += chunks.size();

            StringBuilder dump = new StringBuilder();
            dump.append("문서: ").append(source).append('\n')
                    .append("청크 수: ").append(chunks.size()).append('\n')
                    .append("설정: `##` 헤딩 단위, 상위 `#` 제목 경로 주입, 병합 없음\n");

            List<String> texts = new ArrayList<>();
            for (int i = 0; i < chunks.size(); i++) {
                Document chunk = chunks.get(i);
                String text = chunk.getText();
                texts.add(text);

                // 800토큰 덤프와 같은 잣대로 재려면, 우리가 주입한 경로 줄은 빼고 원본 섹션만 본다.
                String original = text.substring(text.indexOf('\n') + 1);
                boolean startsWithoutHeading = startsWithoutHeading(original);
                boolean endsWithLoneHeading = endsWithLoneHeading(original);
                int tokens = encoding.countTokens(text);

                if (startsWithoutHeading) orphanStart++;
                if (endsWithLoneHeading) loneHeadingEnd++;
                minTokens = Math.min(minTokens, tokens);
                maxTokens = Math.max(maxTokens, tokens);

                dump.append('\n').append("=".repeat(72)).append('\n')
                        .append(String.format("[청크 %d/%d]  토큰 %d · 글자 %d%n",
                                i + 1, chunks.size(), tokens, text.length()))
                        .append("메타데이터: ").append(chunk.getMetadata()).append('\n');

                List<String> warnings = new ArrayList<>();
                if (startsWithoutHeading) warnings.add("헤딩 없이 본문 중간부터 시작");
                if (endsWithLoneHeading) warnings.add("헤딩만 남기고 본문은 다음 청크로");
                if (!warnings.isEmpty()) {
                    dump.append("[!] ").append(String.join(" / ", warnings)).append('\n');
                }

                dump.append("-".repeat(72)).append('\n').append(text).append('\n');

                summary.append("| ").append(source)
                        .append(" | ").append(i + 1)
                        .append(" | ").append(tokens)
                        .append(" | ").append(text.length())
                        .append(" | ").append(chunk.getMetadata().get("heading"))
                        .append(" | ").append(startsWithoutHeading ? "O" : "")
                        .append(" | ").append(endsWithLoneHeading ? "O" : "")
                        .append(" |\n");
            }

            dump.append('\n').append("=".repeat(72)).append('\n')
                    .append("절단 지점 모아보기 - 앞 청크의 끝 ✂ 뒤 청크의 시작\n")
                    .append("=".repeat(72)).append('\n');
            for (int i = 0; i < texts.size() - 1; i++) {
                dump.append('\n').append("[경계 ").append(i + 1).append(" → ").append(i + 2).append("]\n")
                        .append("...").append(tail(texts.get(i), 60)).append('\n')
                        .append("        ✂✂✂ 여기서 잘림 ✂✂✂").append('\n')
                        .append(head(texts.get(i + 1), 60)).append("...").append('\n');
            }

            Path out = OUT_DIR.resolve(source.replace(".md", ".txt"));
            Files.writeString(out, dump.toString(), StandardCharsets.UTF_8);
            System.out.printf("%-20s 청크 %d개 -> %s%n", source, chunks.size(), out);
        }

        summary.append("\n총 문서 ").append(files.size()).append("개, 총 청크 ").append(totalChunks).append("개\n")
                .append("- 헤딩 없이 시작: ").append(orphanStart).append('/').append(totalChunks).append('\n')
                .append("- 헤딩만 남기고 끝: ").append(loneHeadingEnd).append('/').append(totalChunks).append('\n')
                .append("- 토큰 범위: ").append(minTokens).append(" ~ ").append(maxTokens).append('\n');

        Files.writeString(OUT_DIR.resolve("_summary.md"), summary.toString(), StandardCharsets.UTF_8);
        System.out.println("총 청크 수: " + totalChunks
                + " / 헤딩없이시작 " + orphanStart
                + " / 헤딩만남고끝 " + loneHeadingEnd
                + " / 토큰 " + minTokens + "~" + maxTokens);
    }

    private boolean startsWithoutHeading(String text) {
        return text.lines()
                .map(String::strip)
                .filter(line -> !line.isEmpty())
                .findFirst()
                .map(line -> !line.startsWith("#"))
                .orElse(false);
    }

    private boolean endsWithLoneHeading(String text) {
        List<String> lines = text.lines()
                .map(String::strip)
                .filter(line -> !line.isEmpty())
                .toList();
        return !lines.isEmpty() && lines.get(lines.size() - 1).startsWith("#");
    }

    private String tail(String text, int n) {
        String flat = text.strip().replaceAll("\\s+", " ");
        return flat.length() <= n ? flat : flat.substring(flat.length() - n);
    }

    private String head(String text, int n) {
        String flat = text.strip().replaceAll("\\s+", " ");
        return flat.length() <= n ? flat : flat.substring(0, n);
    }
}
