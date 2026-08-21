package com.example.springai.service;

import java.util.List;

import com.example.springai.advisor.GuardrailBlockedException;
import com.example.springai.dto.ChatAction;
import com.example.springai.dto.ChatResponse;
import com.example.springai.dto.Source;
import com.example.springai.tool.UserDataAccessDeniedException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.document.Document;
import reactor.core.publisher.Flux;

@org.springframework.stereotype.Service
public class Service {

    private static final Logger log = LoggerFactory.getLogger(Service.class);
    private static final String USER_DATA_ACCESS_DENIED = "다른 사용자의 개인 데이터는 조회할 수 없습니다.";

    private final QueryRewriter queryRewriter;
    private final Retriever retriever;
    private final AnswerGenerator answerGenerator;

    public Service(QueryRewriter queryRewriter, Retriever retriever, AnswerGenerator answerGenerator) {
        this.queryRewriter = queryRewriter;
        this.retriever = retriever;
        this.answerGenerator = answerGenerator;
    }

    public ChatResponse answer(Long userId, String sessionId, String query) {
        String conversationId = ConversationIds.of(userId, sessionId);

        String searchQuery;
        try {
            searchQuery = queryRewriter.rewrite(query, conversationId);
        }
        catch (GuardrailBlockedException exception) {
            // 가드레일이 막은 요청은 검색도 Tool 호출도 하지 않는다.
            log.info("가드레일 차단 - query=[{}] 사유=[{}]", query, exception.getMessage());
            return ChatResponse.blocked(exception.getMessage());
        }

        var retrievedChunks = retriever.retrieveChunks(searchQuery);

        AnswerGenerator.GeneratedAnswer generated;
        try {
            // 검색은 재작성된 질문으로, 답변은 사용자가 실제로 물어본 원문으로 만든다.
            generated = answerGenerator.generate(query, retrievedChunks, userId, conversationId);
        }
        catch (RuntimeException exception) {
            // 가드레일을 통과한 요청이라도 도구가 소유자 검증에서 막을 수 있다. 마지막 방어선이므로
            // 오류가 아니라 차단으로 응답한다.
            if (!isUserDataAccessDenied(exception)) {
                throw exception;
            }
            log.info("도구 소유자 검증 차단 - query=[{}]", query);
            return ChatResponse.blocked(USER_DATA_ACCESS_DENIED);
        }

        List<String> contexts = retrievedChunks.stream()
                .map(Document::getText)
                .toList();
        List<Source> sources = Sources.from(retrievedChunks);

        ChatResponse response = ChatResponse.answered(
                generated.text(), generated.toolCalls(), contexts, sources);
        log.info("응답 - route={} 근거={}건 도구={}", response.route(), contexts.size(), generated.toolCalls());
        return response;
    }

    /** 도구에서 던진 예외는 모델 호출 과정을 거치며 감싸여 올라오므로 원인 사슬을 따라간다. */
    private boolean isUserDataAccessDenied(Throwable throwable) {
        for (Throwable current = throwable; current != null; current = current.getCause()) {
            if (current instanceof UserDataAccessDeniedException) {
                return true;
            }
        }
        return false;
    }

    /**
     * 스트리밍 응답. 동기 응답과 같은 경로를 지난다.
     *
     * <p>전달 방식만 다를 뿐 가드레일, 질문 재작성, 대화 이력은 동일하게 적용해야 한다.
     * 여기서 우회하면 스트리밍 엔드포인트가 차단을 통과하는 뒷문이 된다.
     *
     * @throws GuardrailBlockedException 가드레일이 요청을 차단한 경우. 호출부가 BLOCK 이벤트로 바꾼다.
     */
    public Flux<String> streamAnswer(Long userId, String sessionId, String query) {
        String conversationId = ConversationIds.of(userId, sessionId);

        String searchQuery = queryRewriter.rewrite(query, conversationId);
        var retrievedChunks = retriever.retrieveChunks(searchQuery);

        return answerGenerator.stream(query, retrievedChunks, userId, conversationId);
    }
}
