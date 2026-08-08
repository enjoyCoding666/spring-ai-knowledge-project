-- Spring AI 知识库项目 PostgreSQL 初始化脚本
-- 适用模型：qwen3-embedding:0.6b，向量维度：1024

BEGIN;

-- Spring AI PgVectorStore 初始化时也会检查这些扩展。
CREATE EXTENSION IF NOT EXISTS vector;
CREATE EXTENSION IF NOT EXISTS hstore;
CREATE EXTENSION IF NOT EXISTS "uuid-ossp";

-- 知识库主表。
CREATE TABLE IF NOT EXISTS t_knowledge_base (
    id                   BIGSERIAL PRIMARY KEY,
    name                 VARCHAR(200) NOT NULL,
    description          TEXT,
    parent_id            BIGINT,
    similarity_threshold DOUBLE PRECISION NOT NULL DEFAULT 0.5,
    create_time          TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time          TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    deleted              INTEGER NOT NULL DEFAULT 0,
    CONSTRAINT fk_knowledge_base_parent
        FOREIGN KEY (parent_id) REFERENCES t_knowledge_base (id) ON DELETE RESTRICT,
    CONSTRAINT chk_knowledge_base_not_self_parent
        CHECK (parent_id IS NULL OR parent_id <> id),
    CONSTRAINT chk_knowledge_base_deleted
        CHECK (deleted IN (0, 1)),
    CONSTRAINT chk_knowledge_base_similarity_threshold
        CHECK (similarity_threshold BETWEEN 0.0 AND 1.0)
);

-- 防止知识库形成直接或跨层循环。
CREATE OR REPLACE FUNCTION prevent_knowledge_base_cycle()
RETURNS TRIGGER AS $$
DECLARE
    has_cycle BOOLEAN;
BEGIN
    IF NEW.parent_id IS NULL THEN
        RETURN NEW;
    END IF;

    WITH RECURSIVE ancestors(id, parent_id) AS (
        SELECT id, parent_id
        FROM t_knowledge_base
        WHERE id = NEW.parent_id

        UNION

        SELECT parent.id, parent.parent_id
        FROM t_knowledge_base parent
        JOIN ancestors child ON parent.id = child.parent_id
    )
    SELECT EXISTS (
        SELECT 1 FROM ancestors WHERE id = NEW.id
    ) INTO has_cycle;

    IF has_cycle THEN
        RAISE EXCEPTION 'Knowledge base hierarchy cannot contain a cycle'
            USING ERRCODE = '23514';
    END IF;

    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

-- 父知识库存在未删除子库时禁止软删除。
CREATE OR REPLACE FUNCTION prevent_parent_soft_delete()
RETURNS TRIGGER AS $$
BEGIN
    IF OLD.deleted = 0
       AND NEW.deleted = 1
       AND EXISTS (
           SELECT 1
           FROM t_knowledge_base child
           WHERE child.parent_id = NEW.id
             AND child.deleted = 0
       ) THEN
        RAISE EXCEPTION 'Knowledge base with active children cannot be deleted'
            USING ERRCODE = '23001';
    END IF;

    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

DROP TRIGGER IF EXISTS trg_prevent_knowledge_base_cycle ON t_knowledge_base;

CREATE TRIGGER trg_prevent_knowledge_base_cycle
BEFORE INSERT OR UPDATE OF parent_id ON t_knowledge_base
FOR EACH ROW
EXECUTE FUNCTION prevent_knowledge_base_cycle();

DROP TRIGGER IF EXISTS trg_prevent_parent_soft_delete ON t_knowledge_base;

CREATE TRIGGER trg_prevent_parent_soft_delete
BEFORE UPDATE OF deleted ON t_knowledge_base
FOR EACH ROW
EXECUTE FUNCTION prevent_parent_soft_delete();

-- 知识文档表，content 保存导入的完整原文。
CREATE TABLE IF NOT EXISTS t_knowledge_document (
    id                BIGSERIAL PRIMARY KEY,
    knowledge_base_id BIGINT NOT NULL,
    title             VARCHAR(500) NOT NULL,
    source_type       VARCHAR(50),
    content           TEXT,
    status            VARCHAR(30) NOT NULL,
    create_time       TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time       TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    deleted           INTEGER NOT NULL DEFAULT 0,
    CONSTRAINT chk_knowledge_document_deleted
        CHECK (deleted IN (0, 1))
);

-- 文档分块表，vector_document_id 对应 t_vector_store.id。
CREATE TABLE IF NOT EXISTS t_document_chunk (
    id                 BIGSERIAL PRIMARY KEY,
    document_id        BIGINT NOT NULL,
    chunk_index        INTEGER NOT NULL,
    content            TEXT NOT NULL,
    vector_document_id VARCHAR(100),
    create_time        TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time        TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    deleted            INTEGER NOT NULL DEFAULT 0,
    CONSTRAINT chk_document_chunk_deleted
        CHECK (deleted IN (0, 1))
);

-- Spring AI PgVectorStore 表结构。
CREATE TABLE IF NOT EXISTS t_vector_store (
    id          UUID PRIMARY KEY,
    content     TEXT,
    metadata    JSON,
    embedding   VECTOR(1024),
    create_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    deleted     INTEGER NOT NULL DEFAULT 0,
    CONSTRAINT chk_vector_store_deleted
        CHECK (deleted IN (0, 1))
);

-- 四张表共用数据库更新时间函数。
CREATE OR REPLACE FUNCTION set_update_time()
RETURNS TRIGGER AS $$
BEGIN
    NEW.update_time = CURRENT_TIMESTAMP;
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

DROP TRIGGER IF EXISTS trg_knowledge_base_update_time ON t_knowledge_base;
CREATE TRIGGER trg_knowledge_base_update_time
BEFORE UPDATE ON t_knowledge_base
FOR EACH ROW EXECUTE FUNCTION set_update_time();

DROP TRIGGER IF EXISTS trg_knowledge_document_update_time ON t_knowledge_document;
CREATE TRIGGER trg_knowledge_document_update_time
BEFORE UPDATE ON t_knowledge_document
FOR EACH ROW EXECUTE FUNCTION set_update_time();

DROP TRIGGER IF EXISTS trg_document_chunk_update_time ON t_document_chunk;
CREATE TRIGGER trg_document_chunk_update_time
BEFORE UPDATE ON t_document_chunk
FOR EACH ROW EXECUTE FUNCTION set_update_time();

DROP TRIGGER IF EXISTS trg_vector_store_update_time ON t_vector_store;
CREATE TRIGGER trg_vector_store_update_time
BEFORE UPDATE ON t_vector_store
FOR EACH ROW EXECUTE FUNCTION set_update_time();

-- 业务查询索引。
CREATE INDEX IF NOT EXISTS idx_knowledge_base_parent_id
    ON t_knowledge_base (parent_id) WHERE deleted = 0;

CREATE INDEX IF NOT EXISTS idx_knowledge_document_base_id
    ON t_knowledge_document (knowledge_base_id) WHERE deleted = 0;

CREATE INDEX IF NOT EXISTS idx_document_chunk_document_id
    ON t_document_chunk (document_id) WHERE deleted = 0;

CREATE INDEX IF NOT EXISTS idx_document_chunk_vector_document_id
    ON t_document_chunk (vector_document_id) WHERE deleted = 0;

-- 与 application.properties 中 HNSW + COSINE_DISTANCE 配置保持一致。
CREATE INDEX IF NOT EXISTS t_vector_store_index
    ON t_vector_store USING HNSW (embedding vector_cosine_ops)
    WHERE deleted = 0;

COMMIT;
