package dev.gitsage.copilot;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class SignatureVerifierTest {

    private final SignatureVerifier verifier = new SignatureVerifier();

    @Test
    void should_allowRequest_when_noSecretConfigured() {
        // In dev mode (no secret), all requests should pass
        boolean result = verifier.verify(null, "any body");
        assertThat(result).isTrue();
    }

    @Test
    void should_allowRequest_when_signatureHeaderIsNull_andNoSecret() {
        boolean result = verifier.verify(null, "test body");
        assertThat(result).isTrue();
    }
}
