-- 为每个知识库增加独立的向量检索相似度阈值。
ALTER TABLE t_knowledge_base
    ADD COLUMN IF NOT EXISTS similarity_threshold DOUBLE PRECISION NOT NULL DEFAULT 0.5;

DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1
        FROM pg_constraint
        WHERE conname = 'chk_knowledge_base_similarity_threshold'
          AND conrelid = 't_knowledge_base'::regclass
    ) THEN
        ALTER TABLE t_knowledge_base
            ADD CONSTRAINT chk_knowledge_base_similarity_threshold
            CHECK (similarity_threshold BETWEEN 0.0 AND 1.0);
    END IF;
END
$$;
