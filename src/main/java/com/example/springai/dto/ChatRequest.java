package com.example.springai.dto;

public record ChatRequest(
        Long userId,
        String message) {
}
