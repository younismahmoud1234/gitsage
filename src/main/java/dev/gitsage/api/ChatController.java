package dev.gitsage.api;

import dev.gitsage.rag.RagService;
import dev.gitsage.rag.RetrievalService;
import io.micronaut.http.MediaType;
import io.micronaut.http.annotation.*;
import io.micronaut.http.sse.Event;
import org.reactivestreams.Publisher;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Sinks;

/**
 * REST API for chatting with your codebase.
 */
@Controller("/api/chat")
public class ChatController {

    private static final Logger LOG = LoggerFactory.getLogger(ChatController.class);

    private final RagService ragService;
    private final RetrievalService retrievalService;

    public ChatController(RagService ragService, RetrievalService retrievalService) {
        this.ragService = ragService;
        this.retrievalService = retrievalService;
    }

    /**
     * Chat endpoint — returns a complete response.
     */
    @Post
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    public ChatResponse chat(@Body ChatRequest request) {
        LOG.info("Chat request: '{}'", request.question());
        var start = System.currentTimeMillis();

        var results = retrievalService.retrieve(request.question());
        var answer = ragService.chat(request.question());
        var duration = System.currentTimeMillis() - start;

        return ChatResponse.of(answer, results.size(), duration);
    }

    /**
     * Streaming chat endpoint — returns tokens via Server-Sent Events.
     */
    @Post("/stream")
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.TEXT_EVENT_STREAM)
    public Publisher<Event<String>> chatStream(@Body ChatRequest request) {
        LOG.info("Streaming chat request: '{}'", request.question());

        Sinks.Many<Event<String>> sink = Sinks.many().unicast().onBackpressureBuffer();

        ragService.chatStream(request.question(), token -> {
            sink.tryEmitNext(Event.of(token));
        }).whenComplete((v, error) -> {
            if (error != null) {
                LOG.error("Stream error: {}", error.getMessage());
                sink.tryEmitNext(Event.of("[ERROR] " + error.getMessage()));
            }
            sink.tryEmitComplete();
        });

        return sink.asFlux();
    }
}
