package com.example.knowledge.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import com.cohere.api.resources.v2.requests.V2RerankRequest;
import com.cohere.api.resources.v2.types.V2RerankResponse;
import com.cohere.api.resources.v2.types.V2RerankResponseResultsItem;
import com.example.knowledge.application.RerankingException;
import com.example.knowledge.domain.ScoreSource;
import com.example.knowledge.domain.SearchHit;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;

class CohereRerankerTest {

    @Test
    void shouldMapCohereIndexesAndScoresToSearchHits() {
        AtomicReference<V2RerankRequest> capturedRequest = new AtomicReference<>();
        CohereReranker reranker = new CohereReranker(request -> {
            capturedRequest.set(request);
            return response(result(1, 0.96F), result(0, 0.61F));
        }, "rerank-v4.0-fast");
        List<SearchHit> candidates = List.of(
                new SearchHit("Guide A", "First candidate.", 0.72),
                new SearchHit("Guide B", "Second candidate.", 0.68));

        List<SearchHit> results = reranker.rerank("Relevant guide", candidates, 2);

        assertThat(results).containsExactly(
                new SearchHit("Guide B", "Second candidate.", 0.96F, ScoreSource.COHERE),
                new SearchHit("Guide A", "First candidate.", 0.61F, ScoreSource.COHERE));
        V2RerankRequest request = capturedRequest.get();
        assertThat(request.getModel()).isEqualTo("rerank-v4.0-fast");
        assertThat(request.getQuery()).isEqualTo("Relevant guide");
        assertThat(request.getTopN()).contains(2);
        assertThat(request.getDocuments()).containsExactly(
                "title: Guide A\ncontent: First candidate.",
                "title: Guide B\ncontent: Second candidate.");
    }

    @Test
    void shouldWrapCohereClientFailure() {
        IllegalStateException clientFailure = new IllegalStateException("Remote failure");
        CohereReranker reranker = new CohereReranker(request -> {
            throw clientFailure;
        }, "rerank-v4.0-fast");

        assertThatThrownBy(() -> reranker.rerank(
                        "Relevant guide",
                        List.of(new SearchHit("Guide A", "First candidate.", 0.72)),
                        1))
                .isInstanceOf(RerankingException.class)
                .hasMessage("Cohere reranking failed")
                .hasCause(clientFailure);
    }

    @Test
    void shouldNotRequestMoreResultsThanCandidates() {
        AtomicReference<V2RerankRequest> capturedRequest = new AtomicReference<>();
        CohereReranker reranker = new CohereReranker(request -> {
            capturedRequest.set(request);
            return response(result(0, 0.88F));
        }, "rerank-v4.0-fast");

        reranker.rerank(
                "Relevant guide",
                List.of(new SearchHit("Guide A", "First candidate.", 0.72)),
                5);

        assertThat(capturedRequest.get().getTopN()).contains(1);
    }

    private V2RerankResponse response(V2RerankResponseResultsItem... results) {
        return V2RerankResponse.builder().results(List.of(results)).build();
    }

    private V2RerankResponseResultsItem result(int index, float score) {
        return V2RerankResponseResultsItem.builder()
                .index(index)
                .relevanceScore(score)
                .build();
    }
}
