package com.example.knowledge.infrastructure;

import com.cohere.api.Cohere;
import com.cohere.api.resources.v2.requests.V2RerankRequest;
import com.cohere.api.resources.v2.types.V2RerankResponse;
import com.example.knowledge.service.rag.RerankingException;
import com.example.knowledge.domain.ScoreSource;
import com.example.knowledge.domain.SearchHit;
import com.example.knowledge.thirdparty.Reranker;
import java.util.List;
import java.util.function.Function;

public class CohereReranker implements Reranker {

    private static final String DOCUMENT_TEMPLATE = "title: %s\ncontent: %s";

    private final Function<V2RerankRequest, V2RerankResponse> rerankClient;
    private final String model;

    public CohereReranker(Cohere cohere, String model) {
        this(request -> cohere.v2().rerank(request), model);
    }

    CohereReranker(
            Function<V2RerankRequest, V2RerankResponse> rerankClient,
            String model) {
        this.rerankClient = rerankClient;
        this.model = model;
    }

    /**
     * 调用 Cohere V2 API 对向量候选进行精排。
     */
    @Override
    public List<SearchHit> rerank(String query, List<SearchHit> candidates, int limit) {
        V2RerankRequest request = V2RerankRequest.builder()
                .model(model)
                .query(query)
                .documents(candidates.stream().map(this::toDocument).toList())
                .topN(Math.min(limit, candidates.size()))
                .build();
        try {
            V2RerankResponse response = rerankClient.apply(request);
            return response.getResults().stream()
                    .map(result -> {
                        SearchHit candidate = candidates.get(result.getIndex());
                        return new SearchHit(
                                candidate.title(),
                                candidate.content(),
                                result.getRelevanceScore(),
                                ScoreSource.COHERE);
                    })
                    .toList();
        } catch (RuntimeException exception) {
            throw new RerankingException("Cohere reranking failed", exception);
        }
    }

    private String toDocument(SearchHit candidate) {
        return DOCUMENT_TEMPLATE.formatted(candidate.title(), candidate.content());
    }
}
