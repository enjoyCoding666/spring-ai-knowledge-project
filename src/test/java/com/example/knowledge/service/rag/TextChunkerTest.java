package com.example.knowledge.service.rag;

import static com.example.knowledge.TestComponents.textChunker;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.ai.document.Document;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.embedding.EmbeddingRequest;
import org.springframework.ai.embedding.EmbeddingResponse;

class TextChunkerTest {

    @Test
    void shouldKeepMarkdownSectionsAndFullHeadingPathsSeparate() {
        TextChunker textChunker = textChunker(1200);

        List<String> chunks = textChunker.split("""
                # Exercise
                General training guidance.

                ## Running
                Run at an easy pace.

                ## Cycling
                Ride on a safe route.
                """);

        assertThat(chunks).containsExactly(
                "Exercise\n\nGeneral training guidance.",
                "Exercise > Running\n\nRun at an easy pace.",
                "Exercise > Cycling\n\nRide on a safe route.");
    }

    @Test
    void shouldBuildHeadingPathWhenMarkdownLevelsAreSkipped() {
        TextChunker textChunker = textChunker(1200);

        List<String> chunks = textChunker.split("""
                # Exercise
                ### Recovery
                Stretch gently after training.
                """);

        assertThat(chunks).containsExactly("Exercise > Recovery\n\nStretch gently after training.");
    }

    @Test
    void shouldNotTreatHeadingsInsideCodeFencesAsSections() {
        TextChunker textChunker = textChunker(1200);

        List<String> chunks = textChunker.split("""
                # Configuration
                ```text
                # This is configuration content
                server.port=8082
                ```
                Configuration explanation.
                """);

        assertThat(chunks).containsExactly("""
                Configuration

                ```text
                # This is configuration content
                server.port=8082
                ```
                Configuration explanation.""");
    }

    @Test
    void shouldSplitOversizedParagraphAtCompleteSentenceBoundaries() {
        TextChunker textChunker = textChunker(35);

        List<String> chunks = textChunker.split("""
                # Recovery
                Back up the configuration first. Restart the service after validation.
                """);

        assertThat(chunks).containsExactly(
                "Recovery\n\nBack up the configuration first.",
                "Recovery\n\nRestart the service after validation.");
    }

    @Test
    void shouldReturnTrimmedTextWhenContentFitsOneChunk() {
        TextChunker textChunker = textChunker(10);

        List<String> chunks = textChunker.split("  hello  ");

        assertThat(chunks).containsExactly("hello");
    }

    @Test
    void shouldSplitAnonymousTextAtDynamicSemanticBoundary() {
        RecordingEmbeddingModel embeddingModel = new RecordingEmbeddingModel();
        TextChunker textChunker = textChunker(embeddingModel, 1, 1200, 0.75, 32);

        List<String> chunks = textChunker.split("""
                Alpha topic details.

                Alpha topic continuation.

                Beta topic details.

                Beta topic continuation.
                """);

        assertThat(chunks).containsExactly(
                "Alpha topic details.\n\nAlpha topic continuation.",
                "Beta topic details.\n\nBeta topic continuation.");
        assertThat(embeddingModel.batches).containsExactly(List.of(
                "Alpha topic details.",
                "Alpha topic continuation.",
                "Beta topic details.",
                "Beta topic continuation."));
    }

    @Test
    void shouldNotCreateSemanticChunkBelowMinimumSize() {
        RecordingEmbeddingModel embeddingModel = new RecordingEmbeddingModel();
        TextChunker textChunker = textChunker(embeddingModel, 100, 1200, 0.75, 32);

        List<String> chunks = textChunker.split("""
                Alpha topic details.

                Alpha topic continuation.

                Beta topic details.

                Beta topic continuation.
                """);

        assertThat(chunks).containsExactly("""
                Alpha topic details.

                Alpha topic continuation.

                Beta topic details.

                Beta topic continuation.""");
    }

    @Test
    void shouldEmbedParagraphsInConfiguredBatches() {
        RecordingEmbeddingModel embeddingModel = new RecordingEmbeddingModel();
        TextChunker textChunker = textChunker(embeddingModel, 1, 1200, 0.75, 2);

        textChunker.split("""
                Alpha one.

                Alpha two.

                Alpha three.

                Alpha four.

                Alpha five.
                """);

        assertThat(embeddingModel.batches)
                .extracting(List::size)
                .containsExactly(2, 2, 1);
    }

    @Test
    void shouldNotEmbedShortTitledSection() {
        RecordingEmbeddingModel embeddingModel = new RecordingEmbeddingModel();
        TextChunker textChunker = textChunker(embeddingModel, 300, 1200, 0.75, 32);

        List<String> chunks = textChunker.split("""
                # Running
                Warm up gently.

                Keep an easy pace.

                Cool down slowly.
                """);

        assertThat(chunks).containsExactly("""
                Running

                Warm up gently.

                Keep an easy pace.

                Cool down slowly.""");
        assertThat(embeddingModel.batches).isEmpty();
    }

    @Test
    void shouldEmbedOversizedTitledSection() {
        RecordingEmbeddingModel embeddingModel = new RecordingEmbeddingModel();
        TextChunker textChunker = textChunker(embeddingModel, 1, 60, 0.75, 32);

        textChunker.split("""
                # Running
                Alpha topic details for a comfortable aerobic training session.

                Alpha topic continuation with relaxed breathing and steady form.

                Beta topic details covering recovery after the training session.
                """);

        assertThat(embeddingModel.batches).isNotEmpty();
    }

    @Test
    void shouldFailImportWhenSemanticEmbeddingIsUnavailable() {
        RecordingEmbeddingModel embeddingModel = new RecordingEmbeddingModel(true);
        TextChunker textChunker = textChunker(embeddingModel, 1, 1200, 0.75, 32);

        assertThatThrownBy(() -> textChunker.split("""
                First paragraph.

                Second paragraph.

                Third paragraph.
                """))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("Embedding service unavailable");
    }

    private static final class RecordingEmbeddingModel implements EmbeddingModel {

        private final List<List<String>> batches = new ArrayList<>();
        private final boolean failEmbedding;

        private RecordingEmbeddingModel() {
            this(false);
        }

        private RecordingEmbeddingModel(boolean failEmbedding) {
            this.failEmbedding = failEmbedding;
        }

        @Override
        public List<float[]> embed(List<String> texts) {
            if (failEmbedding) {
                throw new IllegalStateException("Embedding service unavailable");
            }
            batches.add(List.copyOf(texts));
            return texts.stream().map(this::vectorFor).toList();
        }

        @Override
        public EmbeddingResponse call(EmbeddingRequest request) {
            throw new UnsupportedOperationException();
        }

        @Override
        public float[] embed(Document document) {
            throw new UnsupportedOperationException();
        }

        private float[] vectorFor(String text) {
            if (text.startsWith("Alpha topic details")) {
                return new float[] {1.0F, 0.0F};
            }
            if (text.startsWith("Alpha")) {
                return new float[] {0.99F, 0.01F};
            }
            if (text.startsWith("Beta topic details")) {
                return new float[] {0.0F, 1.0F};
            }
            return new float[] {0.01F, 0.99F};
        }
    }
}
