package com.example.knowledge.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.example.knowledge.domain.KnowledgeChunk;
import com.example.knowledge.domain.KnowledgeDocument;
import com.example.knowledge.domain.KnowledgeImportResult;
import com.example.knowledge.domain.SearchHit;
import com.example.knowledge.port.KnowledgeRepository;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class KnowledgeServiceTest {

    @Test
    void shouldSplitAndSaveDocument() {
        RecordingKnowledgeRepository repository = new RecordingKnowledgeRepository();
        KnowledgeService knowledgeService = new KnowledgeService(repository, new TextChunker(1200));

        KnowledgeImportResult result = knowledgeService.importDocument(
                new KnowledgeDocument(7L, "Training", null, """
                        # Exercise
                        General training guidance.

                        ## Recovery
                        Stretch gently after training.
                        """));

        assertThat(result).isEqualTo(new KnowledgeImportResult(42L, 2));
        assertThat(repository.savedDocument.knowledgeBaseId()).isEqualTo(7L);
        assertThat(repository.savedDocument.sourceType()).isEqualTo("TEXT");
        assertThat(repository.savedChunks)
                .extracting(KnowledgeChunk::content, KnowledgeChunk::chunkIndex)
                .containsExactly(
                        org.assertj.core.groups.Tuple.tuple(
                                "Exercise\n\nGeneral training guidance.", 0),
                        org.assertj.core.groups.Tuple.tuple(
                                "Exercise > Recovery\n\nStretch gently after training.", 1));
        assertThat(repository.savedChunks)
                .extracting(KnowledgeChunk::vectorDocumentId)
                .doesNotHaveDuplicates()
                .allSatisfy(id -> assertThat(id).isNotBlank());
    }

    @Test
    void shouldRejectUnknownKnowledgeBase() {
        RecordingKnowledgeRepository repository = new RecordingKnowledgeRepository();
        repository.knowledgeBaseExists = false;
        KnowledgeService knowledgeService = new KnowledgeService(repository, new TextChunker(10));

        assertThatThrownBy(() -> knowledgeService.importDocument(
                new KnowledgeDocument(99L, "Spring AI", "TEXT", "content")))
                .isInstanceOf(KnowledgeBaseNotFoundException.class)
                .hasMessageContaining("99");

        assertThat(repository.savedChunks).isEmpty();
    }

    private static final class RecordingKnowledgeRepository implements KnowledgeRepository {

        private final List<KnowledgeChunk> savedChunks = new ArrayList<>();
        private boolean knowledgeBaseExists = true;
        private KnowledgeDocument savedDocument;

        @Override
        public boolean existsKnowledgeBase(Long knowledgeBaseId) {
            return knowledgeBaseExists;
        }

        @Override
        public Long save(KnowledgeDocument document, List<KnowledgeChunk> chunks) {
            savedDocument = document;
            savedChunks.addAll(chunks);
            return 42L;
        }

        @Override
        public List<SearchHit> search(Long knowledgeBaseId, String query, int limit) {
            return List.of();
        }
    }
}
