package com.example.springai.dto;

import java.util.List;

/**
 * @param action   답변했는지 차단했는지
 * @param answer   답변 본문, 또는 차단된 경우의 거부 사유
 * @param contexts 답변 근거로 사용한 청크 본문
 * @param sources  각 청크의 출처. 차단된 요청은 비어 있다.
 */
public record ChatResponse(
        ChatAction action,
        String answer,
        List<String> contexts,
        List<Source> sources) {
}
