package dev.gitsage.rag;

import dev.gitsage.config.GitSageConfiguration;
import dev.gitsage.embedding.EmbeddingService;
import dev.gitsage.store.VectorStore;
import dev.gitsage.store.VectorStore.SearchResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@org.mockito.junit.jupiter.MockitoSettings(strictness = org.mockito.quality.Strictness.LENIENT)
class RetrievalServiceTest {

    @Mock private EmbeddingService embeddingService;
    @Mock private VectorStore vectorStore;
    @Mock private GitSageConfiguration config;
    @Mock private GitSageConfiguration.Rag ragConfig;

    private RetrievalService retrievalService;

    @BeforeEach
    void setUp() {
        when(config.rag()).thenReturn(ragConfig);
        when(ragConfig.maxResults()).thenReturn(5);
        when(ragConfig.similarityThreshold()).thenReturn(0.7);
        retrievalService = new RetrievalService(embeddingService, vectorStore, config);
    }

    @Test
    void should_returnRelevantChunks_when_queryMatchesContent() {
        var queryEmbedding = new float[]{0.1f, 0.2f, 0.3f};
        when(embeddingService.embed(anyString())).thenReturn(queryEmbedding);

        var expected = List.of(
                new SearchResult(1L, 1L, "User auth implementation", 0.92,
                        Map.of("repo", "auth-service", "path", "src/Auth.java", "type", "SOURCE_CODE")),
                new SearchResult(2L, 2L, "JWT token handling", 0.85,
                        Map.of("repo", "auth-service", "path", "src/JwtUtil.java", "type", "SOURCE_CODE"))
        );
        when(vectorStore.search(eq(queryEmbedding), eq(5), eq(0.7))).thenReturn(expected);

        var results = retrievalService.retrieve("How does authentication work?");

        assertThat(results).hasSize(2);
        assertThat(results.getFirst().score()).isEqualTo(0.92);
    }

    @Test
    void should_returnEmptyList_when_noMatchesFound() {
        var queryEmbedding = new float[]{0.1f, 0.2f, 0.3f};
        when(embeddingService.embed(anyString())).thenReturn(queryEmbedding);
        when(vectorStore.search(any(), anyInt(), anyDouble())).thenReturn(List.of());

        var results = retrievalService.retrieve("something completely unrelated");

        assertThat(results).isEmpty();
    }

    @Test
    void should_assembleContext_when_resultsExist() {
        var results = List.of(
                new SearchResult(1L, 1L, "public class Auth {}", 0.9,
                        Map.of("repo", "auth-service", "path", "src/Auth.java", "type", "SOURCE_CODE"))
        );

        var context = retrievalService.assembleContext(results);

        assertThat(context).contains("auth-service");
        assertThat(context).contains("src/Auth.java");
        assertThat(context).contains("0.900");
    }

    @Test
    void should_returnFallbackMessage_when_noResults() {
        var context = retrievalService.assembleContext(List.of());
        assertThat(context).contains("No relevant context found");
    }
}
