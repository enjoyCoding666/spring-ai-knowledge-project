package com.example.knowledge.service.rag;

import com.example.knowledge.domain.SearchHit;
import com.example.knowledge.thirdparty.Reranker;
import java.util.List;

public class PassthroughReranker implements Reranker {

    @Override
    public List<SearchHit> rerank(String query, List<SearchHit> candidates, int limit) {
        return candidates.stream().limit(limit).toList();
    }
}
