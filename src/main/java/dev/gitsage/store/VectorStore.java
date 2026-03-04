package dev.gitsage.store;

import java.util.List;
import java.util.Map;

/**
 * Interface for vector storage operations.
 */
public interface VectorStore {

    /**
     * Stores a document chunk with its embedding.
     */
    void store(long documentId, int chunkIndex, String content, float[] embedding, Map<String, String> metadata);

    /**
     * Performs similarity search and returns matching chunks with scores.
     */
    List<SearchResult> search(float[] queryEmbedding, int maxResults, double similarityThreshold);

    /**
     * Performs filtered similarity search.
     */
    List<SearchResult> search(float[] queryEmbedding, int maxResults, double similarityThreshold,
                              Map<String, String> filters);

    /**
     * Deletes all chunks for a given document.
     */
    void deleteByDocumentId(long documentId);

    /**
     * Returns the total number of stored chunks.
     */
    long countChunks();

    record SearchResult(
            long chunkId,
            long documentId,
            String content,
            double score,
            Map<String, String> metadata
    ) {}
}
