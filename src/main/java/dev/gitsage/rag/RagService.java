package dev.gitsage.rag;

import dev.gitsage.config.GitSageConfiguration;
import dev.langchain4j.model.chat.ChatLanguageModel;
import dev.langchain4j.model.chat.StreamingChatLanguageModel;
import dev.langchain4j.model.chat.response.ChatResponse;
import dev.langchain4j.model.chat.response.StreamingChatResponseHandler;
import dev.langchain4j.model.openai.OpenAiChatModel;
import dev.langchain4j.model.openai.OpenAiStreamingChatModel;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.SystemMessage;
import dev.langchain4j.data.message.UserMessage;
import jakarta.annotation.PostConstruct;
import jakarta.inject.Singleton;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;

/**
 * Core RAG orchestrator: retrieves context → builds prompt → calls LLM.
 */
@Singleton
public class RagService {

    private static final Logger LOG = LoggerFactory.getLogger(RagService.class);

    private final RetrievalService retrievalService;
    private final GitSageConfiguration config;
    private ChatLanguageModel chatModel;
    private StreamingChatLanguageModel streamingChatModel;

    public RagService(RetrievalService retrievalService, GitSageConfiguration config) {
        this.retrievalService = retrievalService;
        this.config = config;
    }

    @PostConstruct
    void init() {
        this.chatModel = OpenAiChatModel.builder()
                .apiKey(config.llm().apiKey())
                .modelName(config.llm().model())
                .temperature(0.1)
                .build();

        this.streamingChatModel = OpenAiStreamingChatModel.builder()
                .apiKey(config.llm().apiKey())
                .modelName(config.llm().model())
                .temperature(0.1)
                .build();

        LOG.info("RAG service initialised with model '{}'", config.llm().model());
    }

    /**
     * Processes a chat query: retrieve context → build prompt → call LLM.
     */
    public String chat(String question) {
        LOG.info("Processing chat query: '{}'", truncate(question, 80));

        var results = retrievalService.retrieve(question);
        var context = retrievalService.assembleContext(results);
        var userPrompt = PromptTemplates.buildUserPrompt(context, question);
        var systemPrompt = config.rag().systemPrompt() != null
                ? config.rag().systemPrompt()
                : PromptTemplates.SYSTEM_PROMPT;

        List<ChatMessage> messages = List.of(
                SystemMessage.from(systemPrompt),
                UserMessage.from(userPrompt)
        );

        var response = chatModel.chat(messages);
        return response.aiMessage().text();
    }

    /**
     * Processes a chat query with streaming output.
     */
    public CompletableFuture<Void> chatStream(String question, Consumer<String> tokenConsumer) {
        var results = retrievalService.retrieve(question);
        var context = retrievalService.assembleContext(results);
        var userPrompt = PromptTemplates.buildUserPrompt(context, question);
        var systemPrompt = config.rag().systemPrompt() != null
                ? config.rag().systemPrompt()
                : PromptTemplates.SYSTEM_PROMPT;

        List<ChatMessage> messages = List.of(
                SystemMessage.from(systemPrompt),
                UserMessage.from(userPrompt)
        );

        var future = new CompletableFuture<Void>();

        streamingChatModel.chat(messages, new StreamingChatResponseHandler() {
            @Override
            public void onPartialResponse(String token) {
                tokenConsumer.accept(token);
            }

            @Override
            public void onCompleteResponse(ChatResponse response) {
                future.complete(null);
            }

            @Override
            public void onError(Throwable error) {
                LOG.error("Streaming chat failed: {}", error.getMessage());
                future.completeExceptionally(error);
            }
        });

        return future;
    }

    private String truncate(String text, int maxLength) {
        return text.length() > maxLength ? text.substring(0, maxLength) + "..." : text;
    }
}
