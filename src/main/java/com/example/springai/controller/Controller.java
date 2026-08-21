package com.example.springai.controller;

import com.example.springai.dto.ChatRequest;
import com.example.springai.dto.ChatResponse;
import com.example.springai.dto.ChatStreamEvent;
import com.example.springai.service.Service;
import org.springframework.http.MediaType;
import org.springframework.http.codec.ServerSentEvent;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Flux;

@RestController
@RequestMapping("/api/v1/chat")
public class Controller {

    private final Service service;

    public Controller(Service service) {
        this.service = service;
    }

    @PostMapping(consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
    public ChatResponse chat(@RequestBody ChatRequest request) {
        return service.answer(request.userId(), request.message());
    }

    @PostMapping(
            value = "/stream",
            consumes = MediaType.APPLICATION_JSON_VALUE,
            produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<ServerSentEvent<ChatStreamEvent>> stream(@RequestBody ChatRequest request) {
        Flux<ServerSentEvent<ChatStreamEvent>> tokens = service
                .streamAnswer(request.userId(), request.message())
                .map(content -> sse("token", ChatStreamEvent.token(content)));

        return Flux.concat(
                        Flux.just(sse("connected", ChatStreamEvent.connected())),
                        tokens,
                        Flux.just(sse("completed", ChatStreamEvent.completed())))
                .onErrorResume(error -> Flux.just(sse("error", ChatStreamEvent.error())));
    }

    private ServerSentEvent<ChatStreamEvent> sse(String event, ChatStreamEvent data) {
        return ServerSentEvent.<ChatStreamEvent>builder()
                .event(event)
                .data(data)
                .build();
    }
}
