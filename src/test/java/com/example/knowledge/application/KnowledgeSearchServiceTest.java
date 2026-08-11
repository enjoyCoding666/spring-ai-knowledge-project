package com.example.knowledge.application;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.knowledge.domain.KnowledgeChunk;
import com.example.knowledge.domain.KnowledgeDocument;
import com.example.knowledge.domain.SearchHit;
import com.example.knowledge.port.KnowledgeRepository;
import com.example.knowledge.port.Reranker;
import java.util.List;
import org.junit.jupiter.api.Test;

class KnowledgeSearchServiceTest {

    @Test
    void shouldRerankPgVectorCandidates() {
        SearchHit first = new SearchHit("Guide A", "First candidate.", 0.72);
        SearchHit second = new SearchHit("Guide B", "Second candidate.", 0.68);
        RecordingKnowledgeRepository repository =
                new RecordingKnowledgeRepository(List.of(first, second));
        RecordingReranker reranker = new RecordingReranker(List.of(
                new SearchHit("Guide B", "Second candidate.", 0.96)));
        KnowledgeSearchService searchService =
                new KnowledgeSearchService(repository, reranker, 30);

        List<SearchHit> results = searchService.search(7L, "Which guide is relevant?", 5);

        assertThat(repository.searchLimit).isEqualTo(30);
        assertThat(reranker.limit).isEqualTo(5);
        assertThat(reranker.candidates).containsExactly(first, second);
        assertThat(results).containsExactly(
                new SearchHit("Guide B", "Second candidate.", 0.96));
    }

    @Test
    void shouldNotCallRerankerWhenNoCandidateWasFound() {
        RecordingKnowledgeRepository repository =
                new RecordingKnowledgeRepository(List.of());
        RecordingReranker reranker = new RecordingReranker(List.of());
        KnowledgeSearchService searchService =
                new KnowledgeSearchService(repository, reranker, 30);

        List<SearchHit> results = searchService.search(null, "Unknown topic", 5);

        assertThat(results).isEmpty();
        assertThat(reranker.callCount).isZero();
    }

    @Test
    void shouldFallBackToPgVectorResultsWhenRerankingFails() {
        List<SearchHit> candidates = List.of(
                new SearchHit("Guide A", "First candidate.", 0.82),
                new SearchHit("Guide B", "Second candidate.", 0.74),
                new SearchHit("Guide C", "Third candidate.", 0.63));
        RecordingKnowledgeRepository repository =
                new RecordingKnowledgeRepository(candidates);
        Reranker reranker = (query, hits, limit) -> {
            throw new RerankingException("Remote reranking failed");
        };
        KnowledgeSearchService searchService =
                new KnowledgeSearchService(repository, reranker, 30);

        List<SearchHit> results = searchService.search(7L, "Relevant guide", 2);

        assertThat(results).containsExactlyElementsOf(candidates.subList(0, 2));
    }

    private static final class RecordingKnowledgeRepository implements KnowledgeRepository {

        private final List<SearchHit> hits;
        private Integer searchLimit;

        private RecordingKnowledgeRepository(List<SearchHit> hits) {
            this.hits = hits;
        }

        @Override
        public boolean existsKnowledgeBase(Long knowledgeBaseId) {
            return true;
        }

        @Override
        public Long save(KnowledgeDocument document, List<KnowledgeChunk> chunks) {
            return 1L;
        }

        @Override
        public List<SearchHit> search(Long knowledgeBaseId, String query, int limit) {
            searchLimit = limit;
            return hits;
        }
    }

    private static final class RecordingReranker implements Reranker {

        private final List<SearchHit> results;
        private List<SearchHit> candidates;
        private Integer limit;
        private int callCount;

        private RecordingReranker(List<SearchHit> results) {
            this.results = results;
        }

        @Override
        public List<SearchHit> rerank(String query, List<SearchHit> candidates, int limit) {
            callCount++;
            this.candidates = candidates;
            this.limit = limit;
            return results;
        }
    }
}
