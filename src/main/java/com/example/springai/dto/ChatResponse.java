package com.example.springai.dto;

import java.util.List;

public record ChatResponse(
        ChatAction action,
        String answer,
        List<String> contexts) {
}
