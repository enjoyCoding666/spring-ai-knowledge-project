package com.example.knowledge.service.rag;

import static com.example.knowledge.TestComponents.knowledgeBaseService;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.example.knowledge.domain.KnowledgeBase;
import com.example.knowledge.domain.KnowledgeChunk;
import com.example.knowledge.domain.KnowledgeDocument;
import com.example.knowledge.domain.SearchHit;
import com.example.knowledge.dao.KnowledgeDao;
import java.util.List;
import org.junit.jupiter.api.Test;

class KnowledgeBaseServiceTest {

    @Test
    void shouldCreateKnowledgeBase() {
        RecordingKnowledgeDao repository = new RecordingKnowledgeDao();
        KnowledgeBaseService knowledgeBaseService = knowledgeBaseService(repository);
        KnowledgeBase knowledgeBase =
                new KnowledgeBase("Java Knowledge", "Guides", null, 0.5);

        Long knowledgeBaseId = knowledgeBaseService.createKnowledgeBase(knowledgeBase);

        assertThat(knowledgeBaseId).isEqualTo(42L);
        assertThat(repository.createdKnowledgeBase).isEqualTo(knowledgeBase);
    }

    @Test
    void shouldCreateChildKnowledgeBaseWhenParentExists() {
        RecordingKnowledgeDao repository = new RecordingKnowledgeDao();
        KnowledgeBaseService knowledgeBaseService = knowledgeBaseService(repository);
        KnowledgeBase knowledgeBase =
                new KnowledgeBase("Child Knowledge", null, 7L, null);

        Long knowledgeBaseId = knowledgeBaseService.createKnowledgeBase(knowledgeBase);

        assertThat(knowledgeBaseId).isEqualTo(42L);
        assertThat(repository.checkedParentId).isEqualTo(7L);
    }

    @Test
    void shouldRejectUnknownParentKnowledgeBase() {
        RecordingKnowledgeDao repository = new RecordingKnowledgeDao();
        repository.parentExists = false;
        KnowledgeBaseService knowledgeBaseService = knowledgeBaseService(repository);

        assertThatThrownBy(() -> knowledgeBaseService.createKnowledgeBase(
                new KnowledgeBase("Child Knowledge", null, 99L, null)))
                .isInstanceOf(KnowledgeBaseNotFoundException.class)
                .hasMessageContaining("99");

        assertThat(repository.createdKnowledgeBase).isNull();
    }

    @Test
    void shouldNotCheckParentWhenParentIsNull() {
        RecordingKnowledgeDao repository = new RecordingKnowledgeDao();
        KnowledgeBaseService knowledgeBaseService = knowledgeBaseService(repository);

        knowledgeBaseService.createKnowledgeBase(
                new KnowledgeBase("Root Knowledge", null, null, null));

        assertThat(repository.checkedParentId).isNull();
    }

    private static final class RecordingKnowledgeDao implements KnowledgeDao {

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
