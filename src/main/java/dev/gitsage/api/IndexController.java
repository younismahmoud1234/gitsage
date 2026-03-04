package dev.gitsage.api;

import dev.gitsage.github.GitHubIndexer;
import dev.gitsage.github.GitHubIndexer.IndexingSummary;
import dev.gitsage.store.VectorStore;
import io.micronaut.http.MediaType;
import io.micronaut.http.annotation.*;
import io.micronaut.serde.annotation.Serdeable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * REST API for managing the indexing pipeline.
 */
@Controller("/api/index")
public class IndexController {

    private static final Logger LOG = LoggerFactory.getLogger(IndexController.class);

    private final GitHubIndexer indexer;
    private final VectorStore vectorStore;

    public IndexController(GitHubIndexer indexer, VectorStore vectorStore) {
        this.indexer = indexer;
        this.vectorStore = vectorStore;
    }

    /**
     * Triggers a full indexing of the configured GitHub organisation.
     */
    @Post
    @Produces(MediaType.APPLICATION_JSON)
    public IndexingSummary triggerIndex() {
        LOG.info("Manual indexing triggered");
        return indexer.indexOrg();
    }

    /**
     * Triggers indexing of a single repository.
     */
    @Post("/{repoName}")
    @Produces(MediaType.APPLICATION_JSON)
    public IndexingSummary triggerRepoIndex(@PathVariable String repoName) {
        LOG.info("Manual indexing triggered for repo: {}", repoName);
        int chunks = indexer.indexRepo(repoName);
        return new IndexingSummary(1, chunks, 0, "Completed");
    }

    /**
     * Returns the current indexing status.
     */
    @Get("/status")
    @Produces(MediaType.APPLICATION_JSON)
    public IndexStatus status() {
        return new IndexStatus(indexer.isIndexing(), vectorStore.countChunks());
    }

    @Serdeable
    public record IndexStatus(boolean indexing, long totalChunks) {}
}
