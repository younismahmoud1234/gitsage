package dev.gitsage.api;

import io.micronaut.serde.annotation.Serdeable;

import java.time.Instant;

@Serdeable
public record ChatResponse(
        String answer,
        int sourcesUsed,
        long durationMs,
        Instant timestamp
) {
    public static ChatResponse of(String answer, int sourcesUsed, long durationMs) {
        return new ChatResponse(answer, sourcesUsed, durationMs, Instant.now());
    }
}
