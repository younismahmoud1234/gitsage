package dev.gitsage.github;

import io.micronaut.serde.annotation.Serdeable;

import java.time.Instant;
import java.util.Map;

/**
 * Represents an indexed document from a GitHub repository.
 */
@Serdeable
public record GitHubDocument(
        String repoName,
        String filePath,
        DocType docType,
        String language,
        String content,
        String contentHash,
        Map<String, String> metadata,
        Instant lastUpdated
) {

    public enum DocType {
        README,
        SOURCE_CODE,
        ISSUE,
        PULL_REQUEST,
        DISCUSSION
    }

    public static GitHubDocument of(String repoName, String filePath, DocType docType,
                                     String content, Map<String, String> metadata) {
        return new GitHubDocument(
                repoName,
                filePath,
                docType,
                detectLanguage(filePath),
                content,
                hashContent(content),
                metadata,
                Instant.now()
        );
    }

    private static String detectLanguage(String filePath) {
        if (filePath == null) return null;
        var ext = filePath.contains(".") ? filePath.substring(filePath.lastIndexOf('.') + 1) : "";
        return switch (ext.toLowerCase()) {
            case "java" -> "Java";
            case "kt", "kts" -> "Kotlin";
            case "py" -> "Python";
            case "js", "mjs" -> "JavaScript";
            case "ts", "tsx" -> "TypeScript";
            case "go" -> "Go";
            case "rs" -> "Rust";
            case "rb" -> "Ruby";
            case "md" -> "Markdown";
            case "yml", "yaml" -> "YAML";
            case "json" -> "JSON";
            case "xml" -> "XML";
            case "sql" -> "SQL";
            case "sh", "bash" -> "Shell";
            case "dockerfile" -> "Dockerfile";
            default -> ext.isEmpty() ? null : ext;
        };
    }

    private static String hashContent(String content) {
        try {
            var digest = java.security.MessageDigest.getInstance("SHA-256");
            var hash = digest.digest(content.getBytes(java.nio.charset.StandardCharsets.UTF_8));
            var sb = new StringBuilder();
            for (byte b : hash) {
                sb.append(String.format("%02x", b));
            }
            return sb.toString();
        } catch (Exception e) {
            return String.valueOf(content.hashCode());
        }
    }
}
