package dev.gitsage.github;

import dev.gitsage.config.GitSageConfiguration;
import io.micronaut.scheduling.annotation.Scheduled;
import jakarta.inject.Singleton;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Schedules periodic re-indexing of the GitHub organisation.
 */
@Singleton
public class IndexingScheduler {

    private static final Logger LOG = LoggerFactory.getLogger(IndexingScheduler.class);

    private final GitHubIndexer indexer;
    private final GitSageConfiguration config;

    public IndexingScheduler(GitHubIndexer indexer, GitSageConfiguration config) {
        this.indexer = indexer;
        this.config = config;
    }

    @Scheduled(cron = "${gitsage.indexing.cron}")
    void scheduledIndex() {
        LOG.info("Scheduled indexing triggered for org '{}'", config.github().org());
        try {
            var summary = indexer.indexOrg();
            LOG.info("Scheduled indexing complete: {}", summary);
        } catch (Exception e) {
            LOG.error("Scheduled indexing failed: {}", e.getMessage(), e);
        }
    }
}
