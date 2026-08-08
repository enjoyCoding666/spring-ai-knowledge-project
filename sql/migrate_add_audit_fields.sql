-- 为全部业务表和向量表增加统一审计字段与软删除能力。
BEGIN;

DO $$
DECLARE
    target_table TEXT;
BEGIN
    FOREACH target_table IN ARRAY ARRAY[
        't_knowledge_base',
        't_knowledge_document',
        't_document_chunk'
    ] LOOP
        IF EXISTS (
            SELECT 1
            FROM information_schema.columns
            WHERE table_schema = 'public'
              AND table_name = target_table
              AND column_name = 'created_at'
        ) AND NOT EXISTS (
            SELECT 1
            FROM information_schema.columns
            WHERE table_schema = 'public'
              AND table_name = target_table
              AND column_name = 'create_time'
        ) THEN
            EXECUTE format(
                'ALTER TABLE %I RENAME COLUMN created_at TO create_time',
                target_table
            );
        END IF;
    END LOOP;
END
$$;

ALTER TABLE t_knowledge_base
    ADD COLUMN IF NOT EXISTS create_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    ADD COLUMN IF NOT EXISTS update_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    ADD COLUMN IF NOT EXISTS deleted INTEGER NOT NULL DEFAULT 0;

ALTER TABLE t_knowledge_document
    ADD COLUMN IF NOT EXISTS create_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    ADD COLUMN IF NOT EXISTS update_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    ADD COLUMN IF NOT EXISTS deleted INTEGER NOT NULL DEFAULT 0;

ALTER TABLE t_document_chunk
    ADD COLUMN IF NOT EXISTS create_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    ADD COLUMN IF NOT EXISTS update_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    ADD COLUMN IF NOT EXISTS deleted INTEGER NOT NULL DEFAULT 0;

ALTER TABLE t_vector_store
    ADD COLUMN IF NOT EXISTS create_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    ADD COLUMN IF NOT EXISTS update_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    ADD COLUMN IF NOT EXISTS deleted INTEGER NOT NULL DEFAULT 0;

DO $$
DECLARE
    target_table TEXT;
    constraint_name TEXT;
BEGIN
    FOREACH target_table IN ARRAY ARRAY[
        't_knowledge_base',
        't_knowledge_document',
        't_document_chunk',
        't_vector_store'
    ] LOOP
        constraint_name := 'chk_' || replace(target_table, 't_', '') || '_deleted';
        IF NOT EXISTS (
            SELECT 1
            FROM pg_constraint
            WHERE conname = constraint_name
              AND conrelid = target_table::regclass
        ) THEN
            EXECUTE format(
                'ALTER TABLE %I ADD CONSTRAINT %I CHECK (deleted IN (0, 1))',
                target_table,
                constraint_name
            );
        END IF;
    END LOOP;
END
$$;

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

DROP TRIGGER IF EXISTS trg_prevent_parent_soft_delete ON t_knowledge_base;
CREATE TRIGGER trg_prevent_parent_soft_delete
BEFORE UPDATE OF deleted ON t_knowledge_base
FOR EACH ROW EXECUTE FUNCTION prevent_parent_soft_delete();

DROP INDEX IF EXISTS idx_knowledge_base_parent_id;
CREATE INDEX idx_knowledge_base_parent_id
    ON t_knowledge_base (parent_id) WHERE deleted = 0;

DROP INDEX IF EXISTS idx_knowledge_document_base_id;
CREATE INDEX idx_knowledge_document_base_id
    ON t_knowledge_document (knowledge_base_id) WHERE deleted = 0;

DROP INDEX IF EXISTS idx_document_chunk_document_id;
CREATE INDEX idx_document_chunk_document_id
    ON t_document_chunk (document_id) WHERE deleted = 0;

DROP INDEX IF EXISTS idx_document_chunk_vector_document_id;
CREATE INDEX idx_document_chunk_vector_document_id
    ON t_document_chunk (vector_document_id) WHERE deleted = 0;

DROP INDEX IF EXISTS t_vector_store_index;
CREATE INDEX t_vector_store_index
    ON t_vector_store USING HNSW (embedding vector_cosine_ops)
    WHERE deleted = 0;

COMMIT;
