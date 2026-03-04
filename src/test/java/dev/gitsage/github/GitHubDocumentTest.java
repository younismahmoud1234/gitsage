package dev.gitsage.github;

import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class GitHubDocumentTest {

    @Test
    void should_detectJavaLanguage() {
        var doc = GitHubDocument.of("my-repo", "src/Main.java",
                GitHubDocument.DocType.SOURCE_CODE, "class Main {}", Map.of());

        assertThat(doc.language()).isEqualTo("Java");
    }

    @Test
    void should_detectPythonLanguage() {
        var doc = GitHubDocument.of("my-repo", "app.py",
                GitHubDocument.DocType.SOURCE_CODE, "print('hello')", Map.of());

        assertThat(doc.language()).isEqualTo("Python");
    }

    @Test
    void should_detectMarkdownLanguage() {
        var doc = GitHubDocument.of("my-repo", "README.md",
                GitHubDocument.DocType.README, "# Hello", Map.of());

        assertThat(doc.language()).isEqualTo("Markdown");
    }

    @Test
    void should_generateConsistentContentHash() {
        var doc1 = GitHubDocument.of("repo", "file.java",
                GitHubDocument.DocType.SOURCE_CODE, "same content", Map.of());
        var doc2 = GitHubDocument.of("repo", "file.java",
                GitHubDocument.DocType.SOURCE_CODE, "same content", Map.of());

        assertThat(doc1.contentHash()).isEqualTo(doc2.contentHash());
    }

    @Test
    void should_generateDifferentHash_when_contentDiffers() {
        var doc1 = GitHubDocument.of("repo", "file.java",
                GitHubDocument.DocType.SOURCE_CODE, "content A", Map.of());
        var doc2 = GitHubDocument.of("repo", "file.java",
                GitHubDocument.DocType.SOURCE_CODE, "content B", Map.of());

        assertThat(doc1.contentHash()).isNotEqualTo(doc2.contentHash());
    }

    @Test
    void should_handleNullFilePath() {
        var doc = GitHubDocument.of("repo", null,
                GitHubDocument.DocType.ISSUE, "issue body", Map.of());

        assertThat(doc.language()).isNull();
    }
}
