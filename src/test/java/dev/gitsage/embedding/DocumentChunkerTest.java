package dev.gitsage.embedding;

import dev.gitsage.config.GitSageConfiguration;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class DocumentChunkerTest {

    private DocumentChunker chunker;

    @BeforeEach
    void setUp() {
        var config = mock(GitSageConfiguration.class);
        var ragConfig = mock(GitSageConfiguration.Rag.class);
        when(config.rag()).thenReturn(ragConfig);
        when(ragConfig.chunkSize()).thenReturn(200);
        when(ragConfig.chunkOverlap()).thenReturn(50);
        chunker = new DocumentChunker(config);
    }

    @Test
    void should_returnSingleChunk_when_contentSmallerThanChunkSize() {
        var content = "This is a short piece of content.";
        var chunks = chunker.chunk(content);

        assertThat(chunks).hasSize(1);
        assertThat(chunks.getFirst()).isEqualTo(content);
    }

    @Test
    void should_returnEmptyList_when_contentIsNull() {
        assertThat(chunker.chunk(null)).isEmpty();
    }

    @Test
    void should_returnEmptyList_when_contentIsBlank() {
        assertThat(chunker.chunk("")).isEmpty();
        assertThat(chunker.chunk("   ")).isEmpty();
    }

    @Test
    void should_splitAtMarkdownHeaders() {
        var content = """
                # Introduction
                This is the intro section with enough text to fill a reasonable amount of space for testing.
                
                # Getting Started
                This section explains how to get started with the project and configure it properly.
                
                # API Reference
                This section documents the API endpoints available and how to use them correctly.
                """;

        var chunks = chunker.chunk(content);
        assertThat(chunks).hasSizeGreaterThan(1);
    }

    @Test
    void should_splitAtCodeBoundaries() {
        var content = """
                public class UserService {
                    private final UserRepository repo;
                    
                    public UserService(UserRepository repo) {
                        this.repo = repo;
                    }
                    
                    public User findById(String id) {
                        return repo.findById(id).orElseThrow();
                    }
                }
                
                public class OrderService {
                    private final OrderRepository repo;
                    
                    public OrderService(OrderRepository repo) {
                        this.repo = repo;
                    }
                    
                    public Order createOrder(OrderRequest request) {
                        return repo.save(new Order(request));
                    }
                }
                """;

        var chunks = chunker.chunk(content);
        assertThat(chunks).hasSizeGreaterThan(1);
    }

    @Test
    void should_handleContentWithOnlyWhitespace() {
        assertThat(chunker.chunk("\n\n\n")).isEmpty();
    }
}
