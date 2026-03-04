package dev.gitsage.config;

import io.micronaut.context.annotation.ConfigurationProperties;
import io.micronaut.core.annotation.NonNull;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;

@ConfigurationProperties("gitsage")
public record GitSageConfiguration(
        @NonNull GitHub github,
        @NonNull Llm llm,
        @NonNull Indexing indexing,
        @NonNull Rag rag
) {

    @ConfigurationProperties("github")
    public record GitHub(
            @NotBlank String token,
            @NotBlank String org,
            boolean indexReadmes,
            boolean indexSourceCode,
            boolean indexIssues,
            boolean indexPullRequests
    ) {}

    @ConfigurationProperties("llm")
    public record Llm(
            @NotBlank String provider,
            @NotBlank String apiKey,
            @NotBlank String model,
            @NotBlank String embeddingModel,
            @Positive int embeddingDimensions
    ) {}

    @ConfigurationProperties("indexing")
    public record Indexing(
            @NotBlank String cron,
            @Positive int batchSize,
            boolean incrementalOnly
    ) {}

    @ConfigurationProperties("rag")
    public record Rag(
            @Positive int maxResults,
            double similarityThreshold,
            @Positive int chunkSize,
            @Positive int chunkOverlap,
            @NotBlank String systemPrompt
    ) {}
}
