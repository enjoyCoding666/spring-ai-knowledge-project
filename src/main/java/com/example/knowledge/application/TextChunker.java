package com.example.knowledge.application;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import org.springframework.ai.embedding.EmbeddingModel;

public class TextChunker {

    private final int minimumSize;
    private final int maximumSize;
    private final MarkdownSectionParser sectionParser = new MarkdownSectionParser();
    private final ParagraphSplitter paragraphSplitter = new ParagraphSplitter();
    private final SemanticBoundaryDetector boundaryDetector;

    public TextChunker(int maximumSize) {
        if (maximumSize <= 0) {
            throw new IllegalArgumentException("Chunk size must be positive");
        }
        this.minimumSize = 0;
        this.maximumSize = maximumSize;
        this.boundaryDetector = null;
    }

    public TextChunker(
            EmbeddingModel embeddingModel,
            int minimumSize,
            int maximumSize,
            double breakPercentile,
            int semanticBatchSize) {
        if (minimumSize <= 0 || maximumSize < minimumSize) {
            throw new IllegalArgumentException("Chunk size range is invalid");
        }
        this.minimumSize = minimumSize;
        this.maximumSize = maximumSize;
        this.boundaryDetector =
                new SemanticBoundaryDetector(embeddingModel, breakPercentile, semanticBatchSize);
    }

    /**
     * 按 Markdown 结构和相邻段落语义切分文本。
     */
    public List<String> split(String content) {
        String normalizedContent = content == null ? "" : content.trim();
        if (normalizedContent.isEmpty()) {
            return List.of();
        }

        List<String> chunks = new ArrayList<>();
        for (MarkdownSection section : sectionParser.parse(normalizedContent)) {
            splitSection(section, chunks);
        }
        return List.copyOf(chunks);
    }

    private void splitSection(MarkdownSection section, List<String> chunks) {
        List<String> units = paragraphSplitter.split(section.content(), maximumSize);
        Set<Integer> semanticBreaks = shouldDetectSemanticBreaks(section)
                ? boundaryDetector.detectBreaks(units)
                : Set.of();
        StringBuilder current = new StringBuilder();
        for (int index = 0; index < units.size(); index++) {
            String unit = units.get(index);
            boolean semanticBreak = semanticBreaks.contains(index)
                    && current.length() >= minimumSize;
            boolean sizeBreak = !current.isEmpty()
                    && current.length() + 2 + unit.length() > maximumSize;
            if (semanticBreak || sizeBreak) {
                chunks.add(section.render(current.toString()));
                current.setLength(0);
            }
            if (!current.isEmpty()) {
                current.append("\n\n");
            }
            current.append(unit);
        }
        if (!current.isEmpty()) {
            chunks.add(section.render(current.toString()));
        }
    }

    private boolean shouldDetectSemanticBreaks(MarkdownSection section) {
        return boundaryDetector != null
                && (section.headingPath().isEmpty() || section.content().length() > maximumSize);
    }
}
