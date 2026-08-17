package com.example.knowledge.service.rag;

import jakarta.annotation.PostConstruct;
import com.example.knowledge.domain.SearchHit;
import com.example.knowledge.dao.KnowledgeDao;
import com.example.knowledge.thirdparty.Reranker;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
public class KnowledgeSearchService {

    private static final Logger LOGGER = LoggerFactory.getLogger(KnowledgeSearchService.class);

    @Autowired
    private KnowledgeDao knowledgeDao;

    @Autowired
    private Reranker reranker;

    @Value("${app.knowledge.rerank.candidate-limit:30}")
    private int candidateLimit;

    @PostConstruct
    void validateConfiguration() {
        if (candidateLimit <= 0) {
            throw new IllegalArgumentException("Candidate limit must be greater than zero");
        }
    }

    /**
     * 先通过向量检索召回候选，再进行精排。
     */
    public List<SearchHit> search(Long knowledgeBaseId, String query, int limit) {
        List<SearchHit> candidates =
                knowledgeDao.search(knowledgeBaseId, query, candidateLimit);
        if (candidates.isEmpty()) {
            return List.of();
        }
        try {
            return reranker.rerank(query, candidates, limit);
        } catch (RerankingException exception) {
            LOGGER.warn(
                    "Reranking failed; falling back to vector results: {}",
                    exception.getClass().getSimpleName());
            return candidates.stream().limit(limit).toList();
        }
    }
}
