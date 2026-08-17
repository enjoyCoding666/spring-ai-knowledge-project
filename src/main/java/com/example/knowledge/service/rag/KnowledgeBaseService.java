package com.example.knowledge.service.rag;

import com.example.knowledge.domain.KnowledgeBase;
import com.example.knowledge.dao.KnowledgeDao;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@Service
public class KnowledgeBaseService {

    @Autowired
    private KnowledgeDao knowledgeDao;

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
