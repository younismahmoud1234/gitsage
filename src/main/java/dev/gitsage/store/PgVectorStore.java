package dev.gitsage.store;

import com.pgvector.PGvector;
import jakarta.inject.Singleton;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * PostgreSQL + pgvector implementation of VectorStore.
 * Uses cosine similarity for nearest-neighbour search.
 */
@Singleton
public class PgVectorStore implements VectorStore {

    private static final Logger LOG = LoggerFactory.getLogger(PgVectorStore.class);

    private final DataSource dataSource;

    public PgVectorStore(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    @Override
    public void store(long documentId, int chunkIndex, String content, float[] embedding,
                      Map<String, String> metadata) {
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
            LOG.error("Failed to store chunk for document {}, index {}: {}", documentId, chunkIndex, e.getMessage());
            throw new RuntimeException("Vector store insert failed", e);
        }
    }

    @Override
    public List<SearchResult> search(float[] queryEmbedding, int maxResults, double similarityThreshold) {
        return search(queryEmbedding, maxResults, similarityThreshold, Map.of());
    }

    @Override
    public List<SearchResult> search(float[] queryEmbedding, int maxResults, double similarityThreshold,
                                     Map<String, String> filters) {
        var sql = """
                SELECT dc.id, dc.document_id, dc.content, dc.metadata,
                       1 - (dc.embedding <=> ?::vector) AS score
                FROM document_chunks dc
                JOIN documents d ON dc.document_id = d.id
                WHERE 1 - (dc.embedding <=> ?::vector) >= ?
                ORDER BY dc.embedding <=> ?::vector
                LIMIT ?
                """;

        var results = new ArrayList<SearchResult>();
        var vectorStr = vectorToString(queryEmbedding);

        try (var conn = dataSource.getConnection();
             var stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, vectorStr);
            stmt.setString(2, vectorStr);
            stmt.setDouble(3, similarityThreshold);
            stmt.setString(4, vectorStr);
            stmt.setInt(5, maxResults);

            try (var rs = stmt.executeQuery()) {
                while (rs.next()) {
                    results.add(new SearchResult(
                            rs.getLong("id"),
                            rs.getLong("document_id"),
                            rs.getString("content"),
                            rs.getDouble("score"),
                            parseMetadata(rs.getString("metadata"))
                    ));
                }
            }
        } catch (SQLException e) {
            LOG.error("Similarity search failed: {}", e.getMessage());
            throw new RuntimeException("Vector search failed", e);
        }

        LOG.debug("Search returned {} results (threshold: {})", results.size(), similarityThreshold);
        return results;
    }

    @Override
    public void deleteByDocumentId(long documentId) {
        try (var conn = dataSource.getConnection();
             var stmt = conn.prepareStatement("DELETE FROM document_chunks WHERE document_id = ?")) {
            stmt.setLong(1, documentId);
            int deleted = stmt.executeUpdate();
            LOG.debug("Deleted {} chunks for document {}", deleted, documentId);
        } catch (SQLException e) {
            LOG.error("Failed to delete chunks for document {}: {}", documentId, e.getMessage());
        }
    }

    @Override
    public long countChunks() {
        try (var conn = dataSource.getConnection();
             var stmt = conn.prepareStatement("SELECT COUNT(*) FROM document_chunks");
             var rs = stmt.executeQuery()) {
            return rs.next() ? rs.getLong(1) : 0;
        } catch (SQLException e) {
            LOG.error("Failed to count chunks: {}", e.getMessage());
            return 0;
        }
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
        var entries = map.entrySet().iterator();
        while (entries.hasNext()) {
            var entry = entries.next();
            sb.append("\"").append(escapeJson(entry.getKey())).append("\":\"")
              .append(escapeJson(entry.getValue())).append("\"");
            if (entries.hasNext()) sb.append(",");
        }
        sb.append("}");
        return sb.toString();
    }

    private String escapeJson(String str) {
        return str.replace("\\", "\\\\").replace("\"", "\\\"");
    }

    private Map<String, String> parseMetadata(String json) {
        if (json == null || json.equals("{}") || json.isBlank()) return Map.of();
        var map = new HashMap<String, String>();
        // Simple JSON parsing for flat key-value pairs
        var content = json.substring(1, json.length() - 1);
        for (var pair : content.split(",(?=(?:[^\"]*\"[^\"]*\")*[^\"]*$)")) {
            var kv = pair.split(":", 2);
            if (kv.length == 2) {
                map.put(
                    kv[0].strip().replace("\"", ""),
                    kv[1].strip().replace("\"", "")
                );
            }
        }
        return map;
    }
}
