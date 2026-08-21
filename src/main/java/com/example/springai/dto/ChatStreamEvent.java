package com.example.springai.dto;

public record ChatStreamEvent(
        String phase,
        String content) {

    public static ChatStreamEvent connected() {
        return new ChatStreamEvent("CONNECTED", null);
    }

    public static ChatStreamEvent token(String content) {
        return new ChatStreamEvent("TOKEN", content);
    }

    /** 가드레일이 요청을 막은 경우. 동기 응답의 {@code action=BLOCK}에 대응한다. */
    public static ChatStreamEvent blocked(String reason) {
        return new ChatStreamEvent("BLOCK", reason);
    }

    public static ChatStreamEvent completed() {
        return new ChatStreamEvent("COMPLETED", null);
    }

    public static ChatStreamEvent error() {
        return new ChatStreamEvent("ERROR", "응답 생성 중 오류가 발생했습니다.");
    }
}
