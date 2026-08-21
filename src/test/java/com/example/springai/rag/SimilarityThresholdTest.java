package com.example.springai.rag;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import com.example.springai.advisor.GuardrailBlockedException;
import com.example.springai.service.QueryRewriter;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

/**
 * 유사도 임계값을 정하기 위한 측정.
 *
 * <p>임계값을 임의로 고르지 않고, 실제 파이프라인이 검색에 사용하는 문자열로 점수 분포를 재서 정한다.
 * 가드레일이 앞단에서 차단하는 질문은 애초에 검색에 도달하지 않으므로, 구분해야 할 두 집단은 다음과 같다.
 *
 * <ul>
 *   <li>문서가 답을 담고 있는 질문 - 근거 청크가 살아남아야 한다</li>
 *   <li>가드레일은 통과하지만 문서에 없는 학사 질문 - 노이즈 청크가 잘려야 한다</li>
 * </ul>
 *
 * <pre>./gradlew test --tests "*SimilarityThresholdTest*"</pre>
 */
@SpringBootTest
class SimilarityThresholdTest {

    private static final Path GOLDEN_SET = Path.of("golden-set/golden-set.json");
    private static final Path OUT_DIR = Path.of("chunk-dump/threshold");
    private static final int PROBE_TOP_K = 8;

    /** 가드레일은 통과하지만 우리 문서 5개가 다루지 않는 학사 질문. 노이즈 기준선을 잡는 데 쓴다. */
    private static final List<String> OUT_OF_CORPUS = List.of(
            "등록금 분할납부 되나요?",
            "수강신청 정정 기간이 언제야?",
            "복수전공 신청은 어떻게 해?",
            "장학금 신청 자격이 어떻게 돼?",
            "계절학기는 최대 몇 학점까지 들을 수 있어?",
            "타 대학 학점교류는 어떻게 신청해?",
            "교환학생 지원 자격을 알려줘",
            "졸업앨범 촬영은 언제 해?");

    @Autowired
    private QueryRewriter queryRewriter;

    @Autowired
    private VectorStore vectorStore;

    @Test
    void measureSimilarityDistribution() throws IOException {
        Files.createDirectories(OUT_DIR);

        List<Probe> probes = new ArrayList<>();
        for (JsonNode node : goldenSetCases()) {
            if ("ANSWER".equals(node.path("expectedAction").asText())) {
                probes.add(new Probe(node.path("id").asText(), "문서에 있음", node.path("question").asText()));
            }
        }
        for (int i = 0; i < OUT_OF_CORPUS.size(); i++) {
            probes.add(new Probe("outside-%02d".formatted(i + 1), "문서에 없음", OUT_OF_CORPUS.get(i)));
        }

        StringBuilder report = new StringBuilder();
        report.append("# 유사도 점수 분포 측정\n\n")
                .append("검색 문자열은 파이프라인과 동일하게 `QueryRewriter`가 재작성한 질문을 사용한다.\n")
                .append("점수는 코사인 유사도(1 - distance)이며 상위 ").append(PROBE_TOP_K).append("건까지 조회했다.\n\n")
                .append("| 구분 | id | 재작성된 검색어 | 1위 | 2위 | 4위 | 8위 |\n")
                .append("|---|---|---|---:|---:|---:|---:|\n");

        List<Double> inCorpusTop = new ArrayList<>();
        List<Double> outCorpusTop = new ArrayList<>();
        StringBuilder detail = new StringBuilder("\n## 상세\n");

        for (Probe probe : probes) {
            String searchQuery;
            try {
                searchQuery = queryRewriter.rewrite(probe.question(), null);
            }
            catch (GuardrailBlockedException exception) {
                report.append("| ").append(probe.group()).append(" | ").append(probe.id())
                        .append(" | (가드레일 차단: ").append(exception.getMessage()).append(") | - | - | - | - |\n");
                continue;
            }

            List<Document> hits = vectorStore.similaritySearch(SearchRequest.builder()
                    .query(searchQuery)
                    .topK(PROBE_TOP_K)
                    .similarityThresholdAll()
                    .build());

            List<Double> scores = hits.stream().map(Document::getScore).toList();
            if (scores.isEmpty()) {
                continue;
            }
            (probe.group().equals("문서에 있음") ? inCorpusTop : outCorpusTop).add(scores.get(0));

            report.append("| ").append(probe.group())
                    .append(" | ").append(probe.id())
                    .append(" | ").append(searchQuery)
                    .append(" | ").append(fmt(scores, 0))
                    .append(" | ").append(fmt(scores, 1))
                    .append(" | ").append(fmt(scores, 3))
                    .append(" | ").append(fmt(scores, 7))
                    .append(" |\n");

            detail.append("\n**").append(probe.id()).append("** `").append(probe.question()).append("`\n")
                    .append("  검색어: `").append(searchQuery).append("`\n");
            for (int i = 0; i < hits.size(); i++) {
                Document hit = hits.get(i);
                detail.append("  %d. %.3f  %s / %s%n".formatted(
                        i + 1, hit.getScore(), hit.getMetadata().get("source"), hit.getMetadata().get("heading")));
            }
        }

        report.append("\n## 요약\n\n")
                .append("- 문서에 있는 질문 1위 점수: ").append(range(inCorpusTop)).append('\n')
                .append("- 문서에 없는 질문 1위 점수: ").append(range(outCorpusTop)).append('\n');

        if (!inCorpusTop.isEmpty() && !outCorpusTop.isEmpty()) {
            double lowestRelevant = inCorpusTop.stream().mapToDouble(Double::doubleValue).min().orElseThrow();
            double highestNoise = outCorpusTop.stream().mapToDouble(Double::doubleValue).max().orElseThrow();
            report.append("- 두 집단 사이 간격: %.3f ~ %.3f%n".formatted(highestNoise, lowestRelevant));
            report.append("- 중간값 기준 임계값 후보: **%.2f**%n".formatted((highestNoise + lowestRelevant) / 2));
        }

        Files.writeString(OUT_DIR.resolve("_report.md"), report + detail.toString(), StandardCharsets.UTF_8);
        System.out.println(report);
    }

    private String fmt(List<Double> scores, int index) {
        return index < scores.size() ? "%.3f".formatted(scores.get(index)) : "-";
    }

    private String range(List<Double> scores) {
        if (scores.isEmpty()) {
            return "(없음)";
        }
        return "최저 %.3f / 최고 %.3f".formatted(
                scores.stream().mapToDouble(Double::doubleValue).min().orElseThrow(),
                scores.stream().mapToDouble(Double::doubleValue).max().orElseThrow());
    }

    private Iterable<JsonNode> goldenSetCases() throws IOException {
        return new ObjectMapper().readTree(Files.readString(GOLDEN_SET, StandardCharsets.UTF_8)).path("cases");
    }

    private record Probe(String id, String group, String question) {
    }
}
