package com.example.springai.dto;

/**
 * 답변 근거로 사용한 문서의 출처.
 *
 * <p>인제스트 단계에서 청크마다 붙여 둔 메타데이터에서 뽑는다. 화면이 "졸업요건.md · 졸업자격인증
 * 사회봉사영역 · 사회봉사영역 인증기준" 형태로 표시할 수 있도록 문서와 위치를 나눠 담는다.
 *
 * @param document 문서 파일명
 * @param section  문서 안의 상위 구획 제목
 * @param heading  근거가 된 규정 제목
 * @param version  인제스트 시점. 규정이 갱신되면 달라진다.
 */
public record Source(
        String document,
        String section,
        String heading,
        String version) {
}
