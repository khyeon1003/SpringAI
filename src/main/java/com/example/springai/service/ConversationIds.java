package com.example.springai.service;

import java.nio.charset.StandardCharsets;
import java.util.UUID;

import org.springframework.util.StringUtils;

/**
 * 대화 메모리를 구분하는 식별자를 만든다.
 *
 * <p>사용자와 세션을 함께 묶는다. 세션만으로 구분하면 다른 사용자가 같은 세션 값을 보냈을 때
 * 대화가 섞인다.
 *
 * <p>{@code SPRING_AI_CHAT_MEMORY.conversation_id}가 {@code VARCHAR(36)}이므로 길이를 넘기면
 * 같은 입력에 대해 항상 같은 값이 나오는 UUID로 접는다.
 */
public final class ConversationIds {

    private static final int MAX_LENGTH = 36;
    private static final String ANONYMOUS = "anonymous";

    private ConversationIds() {
    }

    /**
     * @return 대화 식별자. 세션이 없으면 {@code null}이며 이 경우 단발성 질의로 처리한다.
     */
    public static String of(Long userId, String sessionId) {
        if (!StringUtils.hasText(sessionId)) {
            return null;
        }

        String raw = (userId == null ? ANONYMOUS : userId.toString()) + ":" + sessionId.strip();
        return raw.length() <= MAX_LENGTH
                ? raw
                : UUID.nameUUIDFromBytes(raw.getBytes(StandardCharsets.UTF_8)).toString();
    }
}
