package com.example.springai.rag;

import org.springframework.ai.document.Document;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 마크다운 헤딩을 기준으로 문서를 자른다.
 *
 * <p>학칙 문서는 이미 `##` 단위로 하나의 완결된 규정을 담고 있으므로, 토큰 수가 아니라
 * 문서가 스스로 표시해 둔 의미 단위를 그대로 청크 경계로 사용한다.
 *
 * <p>규칙은 세 가지다.
 * <ul>
 *   <li>`##` 섹션 하나가 청크 하나가 된다. 내부의 `###`, `####`는 쪼개지 않고 함께 둔다.</li>
 *   <li>`##` 없이 `#` 바로 아래에 본문이 오는 구간도 청크로 만든다. 그렇지 않으면 유실된다.</li>
 *   <li>청크 맨 앞에 `[문서제목 > 상위 # 제목]` 경로를 붙여 맥락을 보존한다.</li>
 * </ul>
 */
public class MarkdownHeadingSplitter {

    public static final String STRATEGY = "heading";

    public List<Document> split(String markdown, String source) {
        List<String> lines = markdown.lines().toList();

        List<Integer> headingIndexes = new ArrayList<>();
        for (int i = 0; i < lines.size(); i++) {
            if (headingLevel(lines.get(i)) > 0) {
                headingIndexes.add(i);
            }
        }

        List<Document> chunks = new ArrayList<>();
        String title = null;
        String currentH1 = null;

        for (int k = 0; k < headingIndexes.size(); k++) {
            int start = headingIndexes.get(k);
            int end = (k + 1 < headingIndexes.size()) ? headingIndexes.get(k + 1) : lines.size();

            String headingLine = lines.get(start).strip();
            int level = headingLevel(headingLine);
            String heading = headingLine.replaceFirst("^#+\\s*", "").strip();

            if (level == 1) {
                if (title == null) {
                    title = heading;
                }
                currentH1 = heading;
            }

            String body = String.join("\n", lines.subList(start + 1, end)).strip();
            if (body.isEmpty()) {
                // 하위 섹션을 담기만 하는 제목 줄. 그 자체로는 근거가 되지 못하므로 버린다.
                continue;
            }

            String path = (currentH1 == null || currentH1.equals(title))
                    ? title
                    : title + " > " + currentH1;

            String text = "[" + path + "]\n" + headingLine + "\n\n" + body;

            Map<String, Object> metadata = new LinkedHashMap<>();
            metadata.put("source", source);
            metadata.put("title", title);
            metadata.put("section", currentH1);
            metadata.put("heading", heading);
            metadata.put("level", level);
            metadata.put("chunkStrategy", STRATEGY);

            chunks.add(new Document(text, metadata));
        }

        return chunks;
    }

    /** `#`는 1, `##`는 2를 반환한다. `###` 이하는 본문으로 취급하여 0을 반환한다. */
    private int headingLevel(String line) {
        if (line.startsWith("## ")) {
            return 2;
        }
        if (line.startsWith("# ")) {
            return 1;
        }
        return 0;
    }
}
