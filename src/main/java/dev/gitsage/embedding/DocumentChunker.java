package dev.gitsage.embedding;

import dev.gitsage.config.GitSageConfiguration;
import jakarta.inject.Singleton;

import java.util.ArrayList;
import java.util.List;

/**
 * Splits documents into chunks with configurable size and overlap.
 * Supports code-aware and markdown-aware splitting.
 */
@Singleton
public class DocumentChunker {

    private final int chunkSize;
    private final int chunkOverlap;

    public DocumentChunker(GitSageConfiguration config) {
        this.chunkSize = config.rag().chunkSize();
        this.chunkOverlap = config.rag().chunkOverlap();
    }

    /**
     * Splits content into overlapping chunks, respecting natural boundaries.
     */
    public List<String> chunk(String content) {
        if (content == null || content.isBlank()) {
            return List.of();
        }

        if (content.length() <= chunkSize) {
            return List.of(content.strip());
        }

        var chunks = new ArrayList<String>();
        var sections = splitByNaturalBoundaries(content);

        var currentChunk = new StringBuilder();
        for (var section : sections) {
            if (currentChunk.length() + section.length() > chunkSize && !currentChunk.isEmpty()) {
                chunks.add(currentChunk.toString().strip());
                // Overlap: keep the tail of the current chunk
                var overlapText = getOverlapText(currentChunk.toString());
                currentChunk = new StringBuilder(overlapText);
            }
            currentChunk.append(section);
        }

        if (!currentChunk.isEmpty()) {
            chunks.add(currentChunk.toString().strip());
        }

        return chunks.stream().filter(c -> !c.isBlank()).toList();
    }

    /**
     * Splits content by natural boundaries: markdown headers, blank lines,
     * class/method declarations, or paragraph breaks.
     */
    private List<String> splitByNaturalBoundaries(String content) {
        var sections = new ArrayList<String>();
        var lines = content.split("\n");
        var current = new StringBuilder();

        for (var line : lines) {
            boolean isBoundary = isMarkdownHeader(line)
                    || isCodeBoundary(line)
                    || line.isBlank();

            if (isBoundary && !current.isEmpty()) {
                sections.add(current.toString());
                current = new StringBuilder();
            }
            current.append(line).append("\n");
        }

        if (!current.isEmpty()) {
            sections.add(current.toString());
        }

        // Split oversized sections by character limit
        var result = new ArrayList<String>();
        for (var section : sections) {
            if (section.length() > chunkSize) {
                result.addAll(splitBySize(section));
            } else {
                result.add(section);
            }
        }

        return result;
    }

    private boolean isMarkdownHeader(String line) {
        return line.startsWith("#");
    }

    private boolean isCodeBoundary(String line) {
        var trimmed = line.strip();
        return trimmed.startsWith("public ") || trimmed.startsWith("private ")
                || trimmed.startsWith("protected ") || trimmed.startsWith("class ")
                || trimmed.startsWith("interface ") || trimmed.startsWith("record ")
                || trimmed.startsWith("enum ") || trimmed.startsWith("def ")
                || trimmed.startsWith("func ") || trimmed.startsWith("fn ")
                || trimmed.startsWith("function ") || trimmed.startsWith("export ");
    }

    private List<String> splitBySize(String text) {
        var parts = new ArrayList<String>();
        int start = 0;
        while (start < text.length()) {
            int end = Math.min(start + chunkSize, text.length());
            // Try to break at a newline
            if (end < text.length()) {
                int lastNewline = text.lastIndexOf('\n', end);
                if (lastNewline > start) {
                    end = lastNewline + 1;
                }
            }
            parts.add(text.substring(start, end));
            start = Math.max(start + 1, end - chunkOverlap);
        }
        return parts;
    }

    private String getOverlapText(String text) {
        if (text.length() <= chunkOverlap) {
            return text;
        }
        var overlap = text.substring(text.length() - chunkOverlap);
        // Try to start at a newline for cleaner overlap
        int firstNewline = overlap.indexOf('\n');
        if (firstNewline > 0 && firstNewline < overlap.length() - 1) {
            return overlap.substring(firstNewline + 1);
        }
        return overlap;
    }
}
