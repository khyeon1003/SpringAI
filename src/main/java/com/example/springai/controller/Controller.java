package com.example.springai.controller;

import com.example.springai.advisor.GuardrailBlockedException;
import com.example.springai.dto.ChatRequest;
import com.example.springai.dto.ChatResponse;
import com.example.springai.dto.ChatStreamEvent;
import com.example.springai.service.Service;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.http.codec.ServerSentEvent;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Flux;

@RestController
@RequestMapping("/api/v1/chat")
public class Controller {

    private static final Logger log = LoggerFactory.getLogger(Controller.class);

    private final Service service;

    public Controller(Service service) {
        this.service = service;
    }

    /**
     * @param includeContexts 근거 청크 본문을 응답에 담을지. RAGAS 평가가 이 값을 쓰므로 기본은 담는다.
     *                        눈으로 확인할 때는 {@code ?contexts=false} 로 끄면 응답이 짧아진다.
     */
    @PostMapping(consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
    public ChatResponse chat(@RequestBody ChatRequest request,
            @RequestParam(name = "contexts", defaultValue = "true") boolean includeContexts) {
        validate(request);
        ChatResponse response = service.answer(request.userId(), request.sessionId(), request.message());
        return includeContexts ? response : response.withoutContexts();
    }

    @PostMapping(
            value = "/stream",
            consumes = MediaType.APPLICATION_JSON_VALUE,
            produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<ServerSentEvent<ChatStreamEvent>> stream(@RequestBody ChatRequest request) {
        validate(request);

        Flux<ServerSentEvent<ChatStreamEvent>> body;
        try {
            body = service.streamAnswer(request.userId(), request.sessionId(), request.message())
                    .map(content -> sse("token", ChatStreamEvent.token(content)));
        }
        catch (GuardrailBlockedException exception) {
            // 가드레일은 스트림을 열기 전에 판정한다. 동기 응답의 action=BLOCK 과 같은 의미다.
            log.info("가드레일 차단(스트리밍) - query=[{}] 사유=[{}]", request.message(), exception.getMessage());
            body = Flux.just(sse("block", ChatStreamEvent.blocked(exception.getMessage())));
        }

        return Flux.concat(
                        Flux.just(sse("connected", ChatStreamEvent.connected())),
                        body,
                        Flux.just(sse("completed", ChatStreamEvent.completed())))
                .onErrorResume(error -> {
                    log.error("스트리밍 응답 실패", error);
                    return Flux.just(sse("error", ChatStreamEvent.error()));
                });
    }

    private void validate(ChatRequest request) {
        if (request == null || !StringUtils.hasText(request.message())) {
            throw new IllegalArgumentException("message must not be blank");
        }
    }

    private ServerSentEvent<ChatStreamEvent> sse(String event, ChatStreamEvent data) {
        return ServerSentEvent.<ChatStreamEvent>builder()
                .event(event)
                .data(data)
                .build();
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ProblemDetail handleIllegalArgument(IllegalArgumentException exception) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, exception.getMessage());
    }

    /**
     * 모델 호출 실패를 원인이 드러나는 응답으로 바꾼다. 특히 인증 실패는 키 설정 문제이지
     * 서버 버그가 아니므로 그대로 알려 준다.
     */
    @ExceptionHandler(RuntimeException.class)
    public ProblemDetail handleUnexpected(RuntimeException exception) {
        log.error("채팅 처리 실패", exception);

        if (isAuthenticationFailure(exception)) {
            return ProblemDetail.forStatusAndDetail(HttpStatus.SERVICE_UNAVAILABLE,
                    "OpenAI API 키가 유효하지 않습니다. .env 의 OPENAI_API_KEY 를 확인하고, "
                            + "셸 환경변수가 이를 덮어쓰고 있지 않은지 확인하십시오.");
        }

        return ProblemDetail.forStatusAndDetail(HttpStatus.INTERNAL_SERVER_ERROR,
                "요청을 처리하지 못했습니다.");
    }

    private boolean isAuthenticationFailure(Throwable throwable) {
        for (Throwable current = throwable; current != null; current = current.getCause()) {
            String message = current.getMessage();
            if (message != null && (message.contains("Incorrect API key") || message.contains("invalid_api_key"))) {
                return true;
            }
        }
        return false;
    }
}
