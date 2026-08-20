package com.example.springai.rag;

import com.knuddels.jtokkit.Encodings;
import com.knuddels.jtokkit.api.Encoding;
import com.knuddels.jtokkit.api.EncodingType;
import org.junit.jupiter.api.Test;
import org.springframework.ai.document.Document;
import org.springframework.ai.reader.TextReader;
import org.springframework.ai.transformer.splitter.TokenTextSplitter;
import org.springframework.core.io.FileSystemResource;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

/**
 * Phase 2 사전 실험 - 800토큰 청킹 결과를 DB에 저장하지 않고 파일로 덤프한다.
 * 어디가 어떻게 잘리는지 눈으로 확인하기 위한 용도이며, 실제 인제스트는 하지 않는다.
 */
class ChunkDumpTest {

    private static final Path DOCS_DIR = Path.of("src/main/resources/docs");
    private static final Path OUT_DIR = Path.of("chunk-dump/token800");

    private static final int CHUNK_SIZE = 800;
    private static final int MIN_CHUNK_SIZE_CHARS = 350;

    private final Encoding encoding = Encodings.newDefaultEncodingRegistry()
            .getEncoding(EncodingType.CL100K_BASE);

    @Test
    void dumpToken800Chunks() throws IOException {
        Files.createDirectories(OUT_DIR);

        TokenTextSplitter splitter = TokenTextSplitter.builder()
                .withChunkSize(CHUNK_SIZE)
                .withMinChunkSizeChars(MIN_CHUNK_SIZE_CHARS)
                .build();

        List<Path> files;
        try (Stream<Path> paths = Files.list(DOCS_DIR)) {
            files = paths.filter(p -> p.toString().endsWith(".md")).sorted().toList();
        }

        StringBuilder summary = new StringBuilder();
        summary.append("# 800토큰 청킹 결과 요약\n\n")
                .append("설정: `TokenTextSplitter.builder().withChunkSize(").append(CHUNK_SIZE)
                .append(").withMinChunkSizeChars(").append(MIN_CHUNK_SIZE_CHARS).append(")`\n\n")
                .append("| 문서 | 청크 | 토큰 | 글자 | 포함 `##` 수 | 헤딩 없이 시작 | 헤딩만 남고 끝 | 문장 중간에서 끝 |\n")
                .append("|---|---:|---:|---:|---:|:---:|:---:|:---:|\n");

        int totalChunks = 0;

        for (Path file : files) {
            String source = file.getFileName().toString();
            List<Document> raw = new TextReader(new FileSystemResource(file)).get();
            List<Document> chunks = splitter.apply(raw);
            totalChunks += chunks.size();

            StringBuilder dump = new StringBuilder();
            dump.append("문서: ").append(source).append('\n')
                    .append("청크 수: ").append(chunks.size()).append('\n')
                    .append("설정: chunkSize=").append(CHUNK_SIZE)
                    .append(", minChunkSizeChars=").append(MIN_CHUNK_SIZE_CHARS).append('\n');

            List<String> texts = new ArrayList<>();
            for (int i = 0; i < chunks.size(); i++) {
                String text = chunks.get(i).getText();
                texts.add(text);

                List<String> headings = headings(text);
                long h2Count = headings.stream().filter(h -> h.startsWith("## ")).count();
                boolean startsWithoutHeading = startsWithoutHeading(text);
                boolean endsWithLoneHeading = endsWithLoneHeading(text);
                boolean endsMidSentence = endsMidSentence(text);

                dump.append('\n').append("=".repeat(72)).append('\n')
                        .append(String.format("[청크 %d/%d]  토큰 %d · 글자 %d%n",
                                i + 1, chunks.size(), encoding.countTokens(text), text.length()))
                        .append("포함 헤딩: ")
                        .append(headings.isEmpty() ? "(없음 - 본문 조각뿐)" : String.join(" / ", headings))
                        .append('\n');

                List<String> warnings = new ArrayList<>();
                if (startsWithoutHeading) warnings.add("헤딩 없이 본문 중간부터 시작");
                if (endsWithLoneHeading) warnings.add("헤딩만 남기고 본문은 다음 청크로");
                if (endsMidSentence) warnings.add("문장이 끝나지 않은 채 절단");
                if (h2Count >= 3) warnings.add("서로 다른 ## 규정 " + h2Count + "개가 한 청크에 혼재");
                if (!warnings.isEmpty()) {
                    dump.append("[!] ").append(String.join(" / ", warnings)).append('\n');
                }

                dump.append("-".repeat(72)).append('\n').append(text).append('\n');

                summary.append("| ").append(source)
                        .append(" | ").append(i + 1)
                        .append(" | ").append(encoding.countTokens(text))
                        .append(" | ").append(text.length())
                        .append(" | ").append(h2Count)
                        .append(" | ").append(startsWithoutHeading ? "O" : "")
                        .append(" | ").append(endsWithLoneHeading ? "O" : "")
                        .append(" | ").append(endsMidSentence ? "O" : "")
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

        summary.append("\n총 문서 ").append(files.size()).append("개, 총 청크 ").append(totalChunks).append("개\n");
        Files.writeString(OUT_DIR.resolve("_summary.md"), summary.toString(), StandardCharsets.UTF_8);
        System.out.println("총 청크 수: " + totalChunks);
    }

    private List<String> headings(String text) {
        return text.lines()
                .map(String::strip)
                .filter(line -> line.startsWith("#"))
                .toList();
    }

    /** 첫 유효 줄이 헤딩이 아니면, 이 본문이 속한 제목은 앞 청크에 남아 있다는 뜻이다. */
    private boolean startsWithoutHeading(String text) {
        return text.lines()
                .map(String::strip)
                .filter(line -> !line.isEmpty())
                .findFirst()
                .map(line -> !line.startsWith("#"))
                .orElse(false);
    }

    /** 마지막 유효 줄이 헤딩이면, 제목만 남고 내용은 다음 청크로 넘어갔다는 뜻이다. */
    private boolean endsWithLoneHeading(String text) {
        List<String> lines = text.lines()
                .map(String::strip)
                .filter(line -> !line.isEmpty())
                .toList();
        return !lines.isEmpty() && lines.get(lines.size() - 1).startsWith("#");
    }

    private boolean endsMidSentence(String text) {
        String trimmed = text.stripTrailing();
        if (trimmed.isEmpty()) {
            return false;
        }
        List<String> lines = trimmed.lines().map(String::strip).filter(l -> !l.isEmpty()).toList();
        if (!lines.isEmpty() && lines.get(lines.size() - 1).startsWith("#")) {
            return false;
        }
        return ".!?:›》)]".indexOf(trimmed.charAt(trimmed.length() - 1)) < 0;
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
