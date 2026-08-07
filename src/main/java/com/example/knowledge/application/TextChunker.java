package com.example.knowledge.application;

import java.util.ArrayList;
import java.util.List;

public class TextChunker {

    private final int chunkSize;
    private final int overlapSize;

    public TextChunker(int chunkSize, int overlapSize) {
        if (chunkSize <= 0) {
            throw new IllegalArgumentException("Chunk size must be positive");
        }
        if (overlapSize < 0 || overlapSize >= chunkSize) {
            throw new IllegalArgumentException("Overlap size must be between zero and chunk size");
        }
        this.chunkSize = chunkSize;
        this.overlapSize = overlapSize;
    }

    /**
     * 将文本切分为带重叠区域的片段。
     */
    public List<String> split(String content) {
        String normalizedContent = content == null ? "" : content.trim();
        if (normalizedContent.isEmpty()) {
            return List.of();
        }

        List<String> chunks = new ArrayList<>();
        int startIndex = 0;
        while (startIndex < normalizedContent.length()) {
            int endIndex = Math.min(startIndex + chunkSize, normalizedContent.length());
            chunks.add(normalizedContent.substring(startIndex, endIndex));
            if (endIndex == normalizedContent.length()) {
                break;
            }
            startIndex = endIndex - overlapSize;
        }
        return List.copyOf(chunks);
    }
}
