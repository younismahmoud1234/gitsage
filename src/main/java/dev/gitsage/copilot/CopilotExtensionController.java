package dev.gitsage.copilot;

import dev.gitsage.copilot.CopilotMessage.*;
import dev.gitsage.rag.RagService;
import io.micronaut.http.HttpRequest;
import io.micronaut.http.HttpResponse;
import io.micronaut.http.HttpStatus;
import io.micronaut.http.MediaType;
import io.micronaut.http.annotation.*;
import io.micronaut.serde.ObjectMapper;
import org.reactivestreams.Publisher;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Sinks;

/**
 * GitHub Copilot Extension endpoint.
 *
 * Implements the Copilot Extensions protocol:
 * - Receives chat messages from GitHub Copilot
 * - Verifies the request signature
 * - Streams RAG-augmented responses back via SSE (OpenAI-compatible format)
 *
 * @see <a href="https://docs.github.com/en/copilot/building-copilot-extensions">Copilot Extensions Docs</a>
 */
@Controller("/copilot")
public class CopilotExtensionController {

    private static final Logger LOG = LoggerFactory.getLogger(CopilotExtensionController.class);

    private final RagService ragService;
    private final SignatureVerifier signatureVerifier;
    private final ObjectMapper objectMapper;

    public CopilotExtensionController(RagService ragService,
                                       SignatureVerifier signatureVerifier,
                                       ObjectMapper objectMapper) {
        this.ragService = ragService;
        this.signatureVerifier = signatureVerifier;
        this.objectMapper = objectMapper;
    }

    /**
     * Main Copilot Extension endpoint.
     * Receives messages from Copilot and streams RAG-powered responses.
     */
    @Post
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces("text/event-stream")
    public HttpResponse<Publisher<String>> handleCopilotRequest(
            HttpRequest<String> httpRequest,
            @Body String rawBody) {

        // Verify GitHub signature
        var signature = httpRequest.getHeaders().get("X-Hub-Signature-256");
        if (!signatureVerifier.verify(signature, rawBody)) {
            LOG.warn("Invalid signature from Copilot request");
            return HttpResponse.status(HttpStatus.UNAUTHORIZED);
        }

        try {
            var request = objectMapper.readValue(rawBody, CopilotRequest.class);
            var userMessage = extractLastUserMessage(request);

            if (userMessage == null || userMessage.isBlank()) {
                return HttpResponse.ok(Flux.just(formatSSE(StreamEvent.done())));
            }

            LOG.info("Copilot request: '{}'", truncate(userMessage, 80));

            Sinks.Many<String> sink = Sinks.many().unicast().onBackpressureBuffer();

            ragService.chatStream(userMessage, token -> {
                sink.tryEmitNext(formatSSE(StreamEvent.token(token)));
            }).whenComplete((v, error) -> {
                if (error != null) {
                    LOG.error("Copilot stream error: {}", error.getMessage());
                }
                sink.tryEmitNext(formatSSE(StreamEvent.done()));
                sink.tryEmitNext("data: [DONE]\n\n");
                sink.tryEmitComplete();
            });

            return HttpResponse.ok(sink.asFlux());
        } catch (Exception e) {
            LOG.error("Failed to process Copilot request: {}", e.getMessage());
            return HttpResponse.serverError(Flux.just(formatSSE(StreamEvent.done())));
        }
    }

    private String extractLastUserMessage(CopilotRequest request) {
        if (request.messages() == null || request.messages().isEmpty()) {
            return null;
        }
        // Find the last user message
        for (int i = request.messages().size() - 1; i >= 0; i--) {
            var msg = request.messages().get(i);
            if ("user".equals(msg.role())) {
                return msg.content();
            }
        }
        return null;
    }

    private String formatSSE(StreamEvent event) {
        try {
            return "data: " + objectMapper.writeValueAsString(event) + "\n\n";
        } catch (Exception e) {
            return "data: {}\n\n";
        }
    }

    private String truncate(String text, int maxLength) {
        return text.length() > maxLength ? text.substring(0, maxLength) + "..." : text;
    }
}
