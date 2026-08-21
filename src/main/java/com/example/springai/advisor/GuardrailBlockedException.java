package com.example.springai.advisor;

public class GuardrailBlockedException extends RuntimeException {

    public GuardrailBlockedException(String message) {
        super(message);
    }
}
