package dev.gitsage.api;

import io.micronaut.serde.annotation.Serdeable;

@Serdeable
public record ChatRequest(
        String question,
        boolean stream
) {
    public ChatRequest {
        if (question == null || question.isBlank()) {
            throw new IllegalArgumentException("Question must not be blank");
        }
    }
}
