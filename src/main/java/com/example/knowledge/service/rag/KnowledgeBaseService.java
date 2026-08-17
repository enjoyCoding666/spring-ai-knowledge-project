package com.example.knowledge.service.rag;

import com.example.knowledge.domain.KnowledgeBase;
import com.example.knowledge.dao.KnowledgeDao;

public class KnowledgeBaseService {

    private final KnowledgeDao knowledgeDao;

    public KnowledgeBaseService(KnowledgeDao knowledgeDao) {
        this.knowledgeDao = knowledgeDao;
    }

    /**
     * 校验父知识库存在后创建新的知识库。
     */
    public Long createKnowledgeBase(KnowledgeBase knowledgeBase) {
        if (knowledgeBase.parentId() != null
                && !knowledgeDao.existsKnowledgeBase(knowledgeBase.parentId())) {
            throw new KnowledgeBaseNotFoundException(knowledgeBase.parentId());
        }
        return knowledgeDao.createKnowledgeBase(knowledgeBase);
    }
}
