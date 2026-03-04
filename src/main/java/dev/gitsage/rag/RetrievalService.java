package dev.gitsage.rag;

import dev.gitsage.config.GitSageConfiguration;
import dev.gitsage.embedding.EmbeddingService;
import dev.gitsage.store.VectorStore;
import dev.gitsage.store.VectorStore.SearchResult;
import jakarta.inject.Singleton;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;

/**
 * Retrieval service — performs similarity search and assembles context.
 */
@Singleton
public class RetrievalService {

    private static final Logger LOG = LoggerFactory.getLogger(RetrievalService.class);

    private final EmbeddingService embeddingService;
    private final VectorStore vectorStore;
    private final GitSageConfiguration config;

    public RetrievalService(EmbeddingService embeddingService, VectorStore vectorStore,
                            GitSageConfiguration config) {
        this.embeddingService = embeddingService;
        this.vectorStore = vectorStore;
        this.config = config;
    }

    /**
     * Retrieves relevant document chunks for a query.
     */
    public List<SearchResult> retrieve(String query) {
        LOG.debug("Retrieving context for query: '{}'", truncate(query, 100));

        var queryEmbedding = embeddingService.embed(query);
        var results = vectorStore.search(
                queryEmbedding,
                config.rag().maxResults(),
                config.rag().similarityThreshold()
        );

        LOG.info("Retrieved {} relevant chunks for query", results.size());
        return results;
    }

    /**
     * Assembles retrieved chunks into a context string for the LLM.
     */
    public String assembleContext(List<SearchResult> results) {
        if (results.isEmpty()) {
            return "No relevant context found in the indexed repositories.";
        }

        var sb = new StringBuilder();
        sb.append("Relevant context from the codebase:\n\n");

        for (int i = 0; i < results.size(); i++) {
            var result = results.get(i);
            var metadata = result.metadata();
            var repo = metadata.getOrDefault("repo", "unknown");
            var path = metadata.getOrDefault("path", "unknown");
            var docType = metadata.getOrDefault("type", "unknown");

            sb.append("--- Source %d (score: %.3f) ---\n".formatted(i + 1, result.score()));
            sb.append("Repository: %s | File: %s | Type: %s\n".formatted(repo, path, docType));
            sb.append(result.content()).append("\n\n");
        }

        return sb.toString();
    }

    private String truncate(String text, int maxLength) {
        return text.length() > maxLength ? text.substring(0, maxLength) + "..." : text;
    }
}
