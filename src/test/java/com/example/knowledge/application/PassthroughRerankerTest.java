package com.example.knowledge.application;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.knowledge.domain.SearchHit;
import java.util.List;
import org.junit.jupiter.api.Test;

class PassthroughRerankerTest {

    @Test
    void shouldKeepPgVectorOrderAndApplyLimit() {
        List<SearchHit> candidates = List.of(
                new SearchHit("Guide A", "First candidate.", 0.82),
                new SearchHit("Guide B", "Second candidate.", 0.74),
                new SearchHit("Guide C", "Third candidate.", 0.63));
        PassthroughReranker reranker = new PassthroughReranker();

        List<SearchHit> results = reranker.rerank("Relevant guide", candidates, 2);

        assertThat(results).containsExactlyElementsOf(candidates.subList(0, 2));
    }
}
