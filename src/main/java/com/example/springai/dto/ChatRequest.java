package com.example.springai.dto;

/**
 * 채팅 요청.
 *
 * <p>{@code message}만 필수이고 나머지는 선택이다. 평가 스크립트는 {@code userId}까지,
 * 부하 테스트는 {@code sessionId}까지 함께 보내므로 두 형태를 모두 받는다.
 *
 * @param message   사용자 질문
 * @param userId    현재 사용자 식별자. 개인 학사정보가 필요한 질문에서 {@code UserTool}이 사용한다.
 * @param sessionId 대화 세션 식별자. 멀티턴 메모리 도입 시 사용한다.
 */
public record ChatRequest(
        String message,
        Long userId,
        String sessionId) {
}
