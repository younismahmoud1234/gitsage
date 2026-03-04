package dev.gitsage.embedding;

import dev.gitsage.config.GitSageConfiguration;
import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.model.openai.OpenAiEmbeddingModel;
import dev.langchain4j.model.output.Response;
import jakarta.annotation.PostConstruct;
import jakarta.inject.Singleton;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;

/**
 * Service for generating text embeddings using LangChain4j.
 * Supports OpenAI embeddings by default, swappable via configuration.
 */
@Singleton
public class EmbeddingService {

    private static final Logger LOG = LoggerFactory.getLogger(EmbeddingService.class);

    private final GitSageConfiguration config;
    private EmbeddingModel embeddingModel;

    public EmbeddingService(GitSageConfiguration config) {
        this.config = config;
    }

    @PostConstruct
    void init() {
        this.embeddingModel = switch (config.llm().provider().toLowerCase()) {
            case "openai" -> OpenAiEmbeddingModel.builder()
                    .apiKey(config.llm().apiKey())
                    .modelName(config.llm().embeddingModel())
                    .dimensions(config.llm().embeddingDimensions())
                    .build();
            default -> throw new IllegalArgumentException(
                    "Unsupported embedding provider: " + config.llm().provider()
                            + ". Supported: openai"
            );
        };
        LOG.info("Embedding service initialised with provider '{}', model '{}'",
                config.llm().provider(), config.llm().embeddingModel());
    }

    /**
     * Generates an embedding vector for a single text.
     */
    public float[] embed(String text) {
        Response<Embedding> response = embeddingModel.embed(text);
        return response.content().vector();
    }

    /**
     * Generates embeddings for a batch of texts.
     */
    public List<float[]> embedBatch(List<String> texts) {
        if (texts.isEmpty()) return List.of();

        LOG.debug("Embedding batch of {} texts", texts.size());
        var segments = texts.stream()
                .map(dev.langchain4j.data.segment.TextSegment::from)
                .toList();

        Response<List<Embedding>> response = embeddingModel.embedAll(segments);
        return response.content().stream()
                .map(Embedding::vector)
                .toList();
    }

    /**
     * Returns the configured embedding dimensions.
     */
    public int dimensions() {
        return config.llm().embeddingDimensions();
    }
}
