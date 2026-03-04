package dev.gitsage.github;

import dev.gitsage.config.GitSageConfiguration;
import dev.gitsage.embedding.DocumentChunker;
import dev.gitsage.embedding.EmbeddingService;
import jakarta.inject.Singleton;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.sql.DataSource;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Crawls a GitHub organisation's repositories and indexes their content
 * into the vector store.
 */
@Singleton
public class GitHubIndexer {

    private static final Logger LOG = LoggerFactory.getLogger(GitHubIndexer.class);
    private static final Set<String> INDEXABLE_EXTENSIONS = Set.of(
            "java", "kt", "kts", "py", "js", "mjs", "ts", "tsx", "go", "rs", "rb",
            "md", "yml", "yaml", "json", "xml", "sql", "sh", "bash", "tf", "hcl",
            "gradle", "toml", "cfg", "ini", "properties"
    );

    private final GitHubClient gitHubClient;
    private final DocumentChunker chunker;
    private final EmbeddingService embeddingService;
    private final DataSource dataSource;
    private final GitSageConfiguration config;
    private final AtomicBoolean indexing = new AtomicBoolean(false);

    public GitHubIndexer(GitHubClient gitHubClient, DocumentChunker chunker,
                         EmbeddingService embeddingService, DataSource dataSource,
                         GitSageConfiguration config) {
        this.gitHubClient = gitHubClient;
        this.chunker = chunker;
        this.embeddingService = embeddingService;
        this.dataSource = dataSource;
        this.config = config;
    }

    /**
     * Indexes all repositories in the configured organisation.
     * Returns a summary of the indexing operation.
     */
    public IndexingSummary indexOrg() {
        if (!indexing.compareAndSet(false, true)) {
            LOG.warn("Indexing already in progress, skipping");
            return new IndexingSummary(0, 0, 0, "Indexing already in progress");
        }

        try {
            LOG.info("Starting indexing for org '{}'", config.github().org());
            var repos = gitHubClient.listOrgRepos();
            var totalDocs = new AtomicInteger(0);
            var totalChunks = new AtomicInteger(0);
            var errors = new AtomicInteger(0);

            for (var repo : repos) {
                var repoName = (String) repo.get("name");
                var archived = Boolean.TRUE.equals(repo.get("archived"));

                if (archived) {
                    LOG.debug("Skipping archived repo: {}", repoName);
                    continue;
                }

                try {
                    int chunks = indexRepo(repoName);
                    totalDocs.incrementAndGet();
                    totalChunks.addAndGet(chunks);
                } catch (Exception e) {
                    LOG.error("Failed to index repo '{}': {}", repoName, e.getMessage());
                    errors.incrementAndGet();
                    updateIndexingState(repoName, "error", e.getMessage());
                }
            }

            LOG.info("Indexing complete: {} repos, {} chunks, {} errors",
                    totalDocs.get(), totalChunks.get(), errors.get());

            return new IndexingSummary(totalDocs.get(), totalChunks.get(), errors.get(), "Completed");
        } finally {
            indexing.set(false);
        }
    }

    /**
     * Indexes a single repository.
     */
    public int indexRepo(String repoName) {
        LOG.info("Indexing repo: {}", repoName);
        int chunksCreated = 0;

        // Index README
        if (config.github().indexReadmes()) {
            var readme = gitHubClient.fetchReadme(repoName);
            if (readme.isPresent()) {
                chunksCreated += indexDocument(repoName, "README.md",
                        GitHubDocument.DocType.README, readme.get());
            }
        }

        // Index source code files
        if (config.github().indexSourceCode()) {
            var branch = gitHubClient.getDefaultBranch(repoName);
            var tree = gitHubClient.listRepoTree(repoName, branch);

            for (var file : tree) {
                var path = (String) file.get("path");
                var type = (String) file.get("type");

                if (!"blob".equals(type) || !isIndexable(path)) continue;

                var content = gitHubClient.fetchFileContent(repoName, path);
                if (content.isPresent() && content.get().length() < 100_000) {
                    chunksCreated += indexDocument(repoName, path,
                            GitHubDocument.DocType.SOURCE_CODE, content.get());
                }
            }
        }

        // Index issues
        if (config.github().indexIssues()) {
            var issues = gitHubClient.fetchIssues(repoName, 100);
            for (var issue : issues) {
                var title = (String) issue.get("title");
                var body = issue.get("body") != null ? (String) issue.get("body") : "";
                var number = issue.get("number").toString();
                var issueContent = "# Issue #%s: %s\n\n%s".formatted(number, title, body);

                chunksCreated += indexDocument(repoName, "issues/" + number,
                        GitHubDocument.DocType.ISSUE, issueContent);
            }
        }

        updateIndexingState(repoName, "completed", null);
        LOG.info("Indexed repo '{}': {} chunks created", repoName, chunksCreated);
        return chunksCreated;
    }

    private int indexDocument(String repoName, String filePath,
                              GitHubDocument.DocType docType, String content) {
        var doc = GitHubDocument.of(repoName, filePath, docType, content,
                Map.of("repo", repoName, "path", filePath, "type", docType.name()));

        // Check if content has changed
        if (config.indexing().incrementalOnly() && !hasContentChanged(repoName, filePath, doc.contentHash())) {
            return 0;
        }

        // Upsert document
        long documentId = upsertDocument(doc);

        // Chunk and embed
        var chunks = chunker.chunk(content);
        var embeddings = embeddingService.embedBatch(chunks);

        // Store chunks with embeddings
        for (int i = 0; i < chunks.size(); i++) {
            storeChunk(documentId, i, chunks.get(i), embeddings.get(i), doc.metadata());
        }

        return chunks.size();
    }

    private long upsertDocument(GitHubDocument doc) {
        var sql = """
                INSERT INTO documents (repo_name, file_path, doc_type, language, content, content_hash, metadata)
                VALUES (?, ?, ?, ?, ?, ?, ?::jsonb)
                ON CONFLICT (repo_name, file_path)
                DO UPDATE SET content = EXCLUDED.content, content_hash = EXCLUDED.content_hash,
                              language = EXCLUDED.language, metadata = EXCLUDED.metadata,
                              updated_at = NOW()
                RETURNING id
                """;

        try (var conn = dataSource.getConnection();
             var stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, doc.repoName());
            stmt.setString(2, doc.filePath());
            stmt.setString(3, doc.docType().name());
            stmt.setString(4, doc.language());
            stmt.setString(5, doc.content());
            stmt.setString(6, doc.contentHash());
            stmt.setString(7, mapToJson(doc.metadata()));

            var rs = stmt.executeQuery();
            if (rs.next()) return rs.getLong(1);
            throw new RuntimeException("No ID returned from upsert");
        } catch (SQLException e) {
            throw new RuntimeException("Failed to upsert document: " + e.getMessage(), e);
        }
    }

    private void storeChunk(long documentId, int chunkIndex, String content,
                            float[] embedding, Map<String, String> metadata) {
        var sql = """
                INSERT INTO document_chunks (document_id, chunk_index, content, embedding, metadata)
                VALUES (?, ?, ?, ?::vector, ?::jsonb)
                ON CONFLICT (document_id, chunk_index)
                DO UPDATE SET content = EXCLUDED.content, embedding = EXCLUDED.embedding,
                              metadata = EXCLUDED.metadata
                """;

        try (var conn = dataSource.getConnection();
             var stmt = conn.prepareStatement(sql)) {
            stmt.setLong(1, documentId);
            stmt.setInt(2, chunkIndex);
            stmt.setString(3, content);
            stmt.setString(4, vectorToString(embedding));
            stmt.setString(5, mapToJson(metadata));
            stmt.executeUpdate();
        } catch (SQLException e) {
            LOG.error("Failed to store chunk {}/{}: {}", documentId, chunkIndex, e.getMessage());
        }
    }

    private boolean hasContentChanged(String repoName, String filePath, String newHash) {
        var sql = "SELECT content_hash FROM documents WHERE repo_name = ? AND file_path = ?";
        try (var conn = dataSource.getConnection();
             var stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, repoName);
            stmt.setString(2, filePath);
            var rs = stmt.executeQuery();
            if (rs.next()) {
                return !newHash.equals(rs.getString("content_hash"));
            }
            return true; // New document
        } catch (SQLException e) {
            return true;
        }
    }

    private void updateIndexingState(String repoName, String status, String errorMessage) {
        var sql = """
                INSERT INTO indexing_state (org_name, repo_name, last_indexed_at, status, error_message, updated_at)
                VALUES (?, ?, NOW(), ?, ?, NOW())
                ON CONFLICT (org_name, repo_name)
                DO UPDATE SET last_indexed_at = NOW(), status = EXCLUDED.status,
                              error_message = EXCLUDED.error_message, updated_at = NOW()
                """;

        try (var conn = dataSource.getConnection();
             var stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, config.github().org());
            stmt.setString(2, repoName);
            stmt.setString(3, status);
            stmt.setString(4, errorMessage);
            stmt.executeUpdate();
        } catch (SQLException e) {
            LOG.error("Failed to update indexing state for {}: {}", repoName, e.getMessage());
        }
    }

    private boolean isIndexable(String path) {
        if (path == null) return false;
        var lower = path.toLowerCase();
        if (lower.contains("node_modules/") || lower.contains("vendor/")
                || lower.contains(".min.") || lower.contains("dist/")) {
            return false;
        }
        var ext = lower.contains(".") ? lower.substring(lower.lastIndexOf('.') + 1) : "";
        return INDEXABLE_EXTENSIONS.contains(ext);
    }

    public boolean isIndexing() {
        return indexing.get();
    }

    private String vectorToString(float[] vector) {
        var sb = new StringBuilder("[");
        for (int i = 0; i < vector.length; i++) {
            if (i > 0) sb.append(",");
            sb.append(vector[i]);
        }
        sb.append("]");
        return sb.toString();
    }

    private String mapToJson(Map<String, String> map) {
        if (map == null || map.isEmpty()) return "{}";
        var sb = new StringBuilder("{");
        var it = map.entrySet().iterator();
        while (it.hasNext()) {
            var e = it.next();
            sb.append("\"").append(e.getKey()).append("\":\"").append(e.getValue()).append("\"");
            if (it.hasNext()) sb.append(",");
        }
        sb.append("}");
        return sb.toString();
    }

    public record IndexingSummary(int reposIndexed, int chunksCreated, int errors, String status) {}
}
