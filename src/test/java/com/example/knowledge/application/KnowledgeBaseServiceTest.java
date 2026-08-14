package com.example.knowledge.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.example.knowledge.domain.KnowledgeBase;
import com.example.knowledge.domain.KnowledgeChunk;
import com.example.knowledge.domain.KnowledgeDocument;
import com.example.knowledge.domain.SearchHit;
import com.example.knowledge.port.KnowledgeRepository;
import java.util.List;
import org.junit.jupiter.api.Test;

class KnowledgeBaseServiceTest {

    @Test
    void shouldCreateKnowledgeBase() {
        RecordingKnowledgeRepository repository = new RecordingKnowledgeRepository();
        KnowledgeBaseService knowledgeBaseService = new KnowledgeBaseService(repository);
        KnowledgeBase knowledgeBase =
                new KnowledgeBase("Java Knowledge", "Guides", null, 0.5);

        Long knowledgeBaseId = knowledgeBaseService.createKnowledgeBase(knowledgeBase);

        assertThat(knowledgeBaseId).isEqualTo(42L);
        assertThat(repository.createdKnowledgeBase).isEqualTo(knowledgeBase);
    }

    @Test
    void shouldCreateChildKnowledgeBaseWhenParentExists() {
        RecordingKnowledgeRepository repository = new RecordingKnowledgeRepository();
        KnowledgeBaseService knowledgeBaseService = new KnowledgeBaseService(repository);
        KnowledgeBase knowledgeBase =
                new KnowledgeBase("Child Knowledge", null, 7L, null);

        Long knowledgeBaseId = knowledgeBaseService.createKnowledgeBase(knowledgeBase);

        assertThat(knowledgeBaseId).isEqualTo(42L);
        assertThat(repository.checkedParentId).isEqualTo(7L);
    }

    @Test
    void shouldRejectUnknownParentKnowledgeBase() {
        RecordingKnowledgeRepository repository = new RecordingKnowledgeRepository();
        repository.parentExists = false;
        KnowledgeBaseService knowledgeBaseService = new KnowledgeBaseService(repository);

        assertThatThrownBy(() -> knowledgeBaseService.createKnowledgeBase(
                new KnowledgeBase("Child Knowledge", null, 99L, null)))
                .isInstanceOf(KnowledgeBaseNotFoundException.class)
                .hasMessageContaining("99");

        assertThat(repository.createdKnowledgeBase).isNull();
    }

    @Test
    void shouldNotCheckParentWhenParentIsNull() {
        RecordingKnowledgeRepository repository = new RecordingKnowledgeRepository();
        KnowledgeBaseService knowledgeBaseService = new KnowledgeBaseService(repository);

        knowledgeBaseService.createKnowledgeBase(
                new KnowledgeBase("Root Knowledge", null, null, null));

        assertThat(repository.checkedParentId).isNull();
    }

    private static final class RecordingKnowledgeRepository implements KnowledgeRepository {

        private KnowledgeBase createdKnowledgeBase;
        private Long checkedParentId;
        private boolean parentExists = true;

        @Override
        public boolean existsKnowledgeBase(Long knowledgeBaseId) {
            checkedParentId = knowledgeBaseId;
            return parentExists;
        }

        @Override
        public Long createKnowledgeBase(KnowledgeBase knowledgeBase) {
            createdKnowledgeBase = knowledgeBase;
            return 42L;
        }

        @Override
        public Long save(KnowledgeDocument document, List<KnowledgeChunk> chunks) {
            return 1L;
        }

        @Override
        public List<SearchHit> search(Long knowledgeBaseId, String query, int limit) {
            return List.of();
        }
    }
}
