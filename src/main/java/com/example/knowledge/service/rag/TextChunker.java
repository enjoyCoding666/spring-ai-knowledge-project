package com.example.knowledge.service.rag;

import jakarta.annotation.PostConstruct;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class TextChunker {

    @Autowired
    private EmbeddingModel embeddingModel;

    @Value("${app.knowledge.chunk-min-size:300}")
    private int minimumSize;

    @Value("${app.knowledge.chunk-max-size:1200}")
    private int maximumSize;

    @Value("${app.knowledge.semantic-break-percentile:0.75}")
    private double breakPercentile;

    @Value("${app.knowledge.semantic-batch-size:32}")
    private int semanticBatchSize;

    private final MarkdownSectionParser sectionParser = new MarkdownSectionParser();
    private final ParagraphSplitter paragraphSplitter = new ParagraphSplitter();
    private SemanticBoundaryDetector boundaryDetector;

    @PostConstruct
    void initialize() {
        if (minimumSize <= 0 || maximumSize < minimumSize) {
            throw new IllegalArgumentException("Chunk size range is invalid");
        }
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
