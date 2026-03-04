package dev.gitsage.copilot;

import dev.gitsage.copilot.CopilotMessage.*;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class CopilotMessageTest {

    @Test
    void should_createTokenEvent_withCorrectFormat() {
        var event = StreamEvent.token("Hello");

        assertThat(event.object()).isEqualTo("chat.completion.chunk");
        assertThat(event.choices()).hasSize(1);
        assertThat(event.choices().getFirst().delta().content()).isEqualTo("Hello");
        assertThat(event.choices().getFirst().finish_reason()).isNull();
    }

    @Test
    void should_createDoneEvent_withStopReason() {
        var event = StreamEvent.done();

        assertThat(event.choices().getFirst().finish_reason()).isEqualTo("stop");
        assertThat(event.choices().getFirst().delta().content()).isEmpty();
    }

    @Test
    void should_createUserMessage() {
        var msg = Message.user("How does auth work?");
        assertThat(msg.role()).isEqualTo("user");
        assertThat(msg.content()).isEqualTo("How does auth work?");
    }

    @Test
    void should_createSystemMessage() {
        var msg = Message.system("You are a helpful assistant.");
        assertThat(msg.role()).isEqualTo("system");
    }
}
