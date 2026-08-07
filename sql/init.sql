-- Spring AI 知识库 Demo PostgreSQL 初始化脚本
-- 适用模型：qwen3-embedding:0.6b，向量维度：1024

BEGIN;

-- Spring AI PgVectorStore 初始化时也会检查这些扩展。
CREATE EXTENSION IF NOT EXISTS vector;
CREATE EXTENSION IF NOT EXISTS hstore;
CREATE EXTENSION IF NOT EXISTS "uuid-ossp";

-- 知识库主表。
CREATE TABLE IF NOT EXISTS t_knowledge_base (
    id          BIGSERIAL PRIMARY KEY,
    name        VARCHAR(200) NOT NULL,
    description TEXT,
    created_at  TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

-- 知识文档表，content 保存导入的完整原文。
CREATE TABLE IF NOT EXISTS t_knowledge_document (
    id                BIGSERIAL PRIMARY KEY,
    knowledge_base_id BIGINT NOT NULL,
    title             VARCHAR(500) NOT NULL,
    source_type       VARCHAR(50),
    content           TEXT,
    status            VARCHAR(30) NOT NULL,
    created_at        TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

-- 文档分块表，vector_document_id 对应 t_vector_store.id。
CREATE TABLE IF NOT EXISTS t_document_chunk (
    id                 BIGSERIAL PRIMARY KEY,
    document_id        BIGINT NOT NULL,
    chunk_index        INTEGER NOT NULL,
    content            TEXT NOT NULL,
    vector_document_id VARCHAR(100),
    created_at         TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

-- Spring AI PgVectorStore 表结构。
CREATE TABLE IF NOT EXISTS t_vector_store (
    id        UUID PRIMARY KEY,
    content   TEXT,
    metadata  JSON,
    embedding VECTOR(1024)
);

-- 业务查询索引。
CREATE INDEX IF NOT EXISTS idx_knowledge_document_base_id
    ON t_knowledge_document (knowledge_base_id);

CREATE INDEX IF NOT EXISTS idx_document_chunk_document_id
    ON t_document_chunk (document_id);

CREATE INDEX IF NOT EXISTS idx_document_chunk_vector_document_id
    ON t_document_chunk (vector_document_id);

-- 与 application.properties 中 HNSW + COSINE_DISTANCE 配置保持一致。
CREATE INDEX IF NOT EXISTS t_vector_store_index
    ON t_vector_store USING HNSW (embedding vector_cosine_ops);

COMMIT;
