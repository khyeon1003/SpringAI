package com.example.springai.controller;

import com.example.springai.dto.ChatRequest;
import com.example.springai.dto.ChatResponse;
import com.example.springai.service.Service;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1")
public class Controller {

    private static final Logger log = LoggerFactory.getLogger(Controller.class);

    private final Service service;

    public Controller(Service service) {
        this.service = service;
    }

    @PostMapping("/chat")
    public ChatResponse chat(@RequestBody ChatRequest request) {
        if (request == null || !StringUtils.hasText(request.message())) {
            throw new IllegalArgumentException("message must not be blank");
        }

        return service.answer(request.userId(), request.sessionId(), request.message());
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
