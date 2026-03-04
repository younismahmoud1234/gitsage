package dev.gitsage.copilot;

import io.micronaut.serde.annotation.Serdeable;

import java.util.List;

/**
 * Message models for the GitHub Copilot Extension protocol.
 * See: https://docs.github.com/en/copilot/building-copilot-extensions
 */
public final class CopilotMessage {

    private CopilotMessage() {}

    @Serdeable
    public record CopilotRequest(
            List<Message> messages
    ) {}

    @Serdeable
    public record Message(
            String role,
            String content
    ) {
        public static Message system(String content) {
            return new Message("system", content);
        }

        public static Message user(String content) {
            return new Message("user", content);
        }

        public static Message assistant(String content) {
            return new Message("assistant", content);
        }
    }

    /**
     * Server-Sent Event data format for Copilot streaming responses.
     * Follows the OpenAI-compatible SSE format.
     */
    @Serdeable
    public record StreamEvent(
            String id,
            String object,
            long created,
            String model,
            List<Choice> choices
    ) {
        public static StreamEvent token(String token) {
            return new StreamEvent(
                    "chatcmpl-gitsage",
                    "chat.completion.chunk",
                    System.currentTimeMillis() / 1000,
                    "gitsage",
                    List.of(new Choice(0, new Delta("assistant", token), null))
            );
        }

        public static StreamEvent done() {
            return new StreamEvent(
                    "chatcmpl-gitsage",
                    "chat.completion.chunk",
                    System.currentTimeMillis() / 1000,
                    "gitsage",
                    List.of(new Choice(0, new Delta("assistant", ""), "stop"))
            );
        }
    }

    @Serdeable
    public record Choice(
            int index,
            Delta delta,
            String finish_reason
    ) {}

    @Serdeable
    public record Delta(
            String role,
            String content
    ) {}
}
