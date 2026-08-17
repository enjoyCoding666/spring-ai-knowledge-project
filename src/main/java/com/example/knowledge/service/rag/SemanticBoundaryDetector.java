package com.example.knowledge.service.rag;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.springframework.ai.embedding.EmbeddingModel;

final class SemanticBoundaryDetector {

    private final EmbeddingModel embeddingModel;
    private final double breakPercentile;
    private final int batchSize;

    SemanticBoundaryDetector(
            EmbeddingModel embeddingModel,
            double breakPercentile,
            int batchSize) {
        if (breakPercentile <= 0.0 || breakPercentile >= 1.0) {
            throw new IllegalArgumentException("Break percentile must be between zero and one");
        }
        if (batchSize <= 0) {
            throw new IllegalArgumentException("Semantic batch size must be positive");
        }
        this.embeddingModel = embeddingModel;
        this.breakPercentile = breakPercentile;
        this.batchSize = batchSize;
    }

    /**
     * 返回应在对应段落索引之前切分的位置。
     */
    Set<Integer> detectBreaks(List<String> paragraphs) {
        if (paragraphs.size() < 3) {
            return Set.of();
        }
        List<float[]> embeddings = embedInBatches(paragraphs);
        List<Double> distances = adjacentDistances(embeddings);
        double threshold = percentile(distances, breakPercentile);
        Set<Integer> breakIndexes = new HashSet<>();
        for (int index = 0; index < distances.size(); index++) {
            if (distances.get(index) > threshold) {
                breakIndexes.add(index + 1);
            }
        }
        return Set.copyOf(breakIndexes);
    }

    private List<float[]> embedInBatches(List<String> paragraphs) {
        List<float[]> embeddings = new ArrayList<>(paragraphs.size());
        for (int start = 0; start < paragraphs.size(); start += batchSize) {
            int end = Math.min(start + batchSize, paragraphs.size());
            List<String> batch = paragraphs.subList(start, end);
            List<float[]> batchEmbeddings = embeddingModel.embed(batch);
            if (batchEmbeddings.size() != batch.size()) {
                throw new IllegalStateException("Embedding count does not match paragraph count");
            }
            embeddings.addAll(batchEmbeddings);
        }
        return embeddings;
    }

    private List<Double> adjacentDistances(List<float[]> embeddings) {
        List<Double> distances = new ArrayList<>(embeddings.size() - 1);
        for (int index = 0; index < embeddings.size() - 1; index++) {
            distances.add(cosineDistance(embeddings.get(index), embeddings.get(index + 1)));
        }
        return distances;
    }

    private double cosineDistance(float[] left, float[] right) {
        if (left.length != right.length) {
            throw new IllegalArgumentException("Embedding dimensions must match");
        }
        double dotProduct = 0.0;
        double leftNorm = 0.0;
        double rightNorm = 0.0;
        for (int index = 0; index < left.length; index++) {
            dotProduct += left[index] * right[index];
            leftNorm += left[index] * left[index];
            rightNorm += right[index] * right[index];
        }
        if (leftNorm == 0.0 || rightNorm == 0.0) {
            return 0.0;
        }
        double similarity = dotProduct / (Math.sqrt(leftNorm) * Math.sqrt(rightNorm));
        return 1.0 - Math.max(-1.0, Math.min(1.0, similarity));
    }

    private double percentile(List<Double> values, double percentile) {
        List<Double> sortedValues = values.stream().sorted().toList();
        double position = percentile * (sortedValues.size() - 1);
        int lowerIndex = (int) Math.floor(position);
        int upperIndex = (int) Math.ceil(position);
        if (lowerIndex == upperIndex) {
            return sortedValues.get(lowerIndex);
        }
        double fraction = position - lowerIndex;
        return sortedValues.get(lowerIndex)
                + fraction * (sortedValues.get(upperIndex) - sortedValues.get(lowerIndex));
    }
}
