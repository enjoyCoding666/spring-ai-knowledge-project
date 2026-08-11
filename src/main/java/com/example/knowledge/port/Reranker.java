package com.example.knowledge.port;

import com.example.knowledge.domain.SearchHit;
import java.util.List;

public interface Reranker {

    /**
     * 根据查询对候选知识片段进行重排并限制结果数量。
     */
    List<SearchHit> rerank(String query, List<SearchHit> candidates, int limit);
}
