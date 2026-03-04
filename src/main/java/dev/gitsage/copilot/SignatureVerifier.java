package dev.gitsage.copilot;

import io.micronaut.context.annotation.Value;
import jakarta.inject.Singleton;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;

/**
 * Verifies GitHub webhook signatures to ensure requests originate from GitHub.
 * Required for production Copilot Extensions.
 */
@Singleton
public class SignatureVerifier {

    private static final Logger LOG = LoggerFactory.getLogger(SignatureVerifier.class);
    private static final String HMAC_SHA256 = "HmacSHA256";

    @Value("${gitsage.copilot.webhook-secret:}")
    private String webhookSecret;

    /**
     * Verifies the X-Hub-Signature-256 header against the request body.
     * If no webhook secret is configured, verification is skipped (dev mode).
     */
    public boolean verify(String signatureHeader, String body) {
        if (webhookSecret == null || webhookSecret.isBlank()) {
            LOG.warn("No webhook secret configured — skipping signature verification (dev mode)");
            return true;
        }

        if (signatureHeader == null || !signatureHeader.startsWith("sha256=")) {
            LOG.warn("Missing or invalid signature header");
            return false;
        }

        try {
            var mac = Mac.getInstance(HMAC_SHA256);
            mac.init(new SecretKeySpec(webhookSecret.getBytes(StandardCharsets.UTF_8), HMAC_SHA256));
            var expectedHash = bytesToHex(mac.doFinal(body.getBytes(StandardCharsets.UTF_8)));
            var actualHash = signatureHeader.substring("sha256=".length());

            return constantTimeEquals(expectedHash, actualHash);
        } catch (NoSuchAlgorithmException | InvalidKeyException e) {
            LOG.error("Signature verification failed: {}", e.getMessage());
            return false;
        }
    }

    private static String bytesToHex(byte[] bytes) {
        var sb = new StringBuilder();
        for (byte b : bytes) {
            sb.append(String.format("%02x", b));
        }
        return sb.toString();
    }

    /**
     * Constant-time comparison to prevent timing attacks.
     */
    private static boolean constantTimeEquals(String a, String b) {
        if (a.length() != b.length()) return false;
        int result = 0;
        for (int i = 0; i < a.length(); i++) {
            result |= a.charAt(i) ^ b.charAt(i);
        }
        return result == 0;
    }
}
