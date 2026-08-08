-- 为知识库增加无限层级父子关系和循环保护。
BEGIN;

ALTER TABLE t_knowledge_base
    ADD COLUMN IF NOT EXISTS parent_id BIGINT;

DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1
        FROM pg_constraint
        WHERE conname = 'fk_knowledge_base_parent'
          AND conrelid = 't_knowledge_base'::regclass
    ) THEN
        ALTER TABLE t_knowledge_base
            ADD CONSTRAINT fk_knowledge_base_parent
            FOREIGN KEY (parent_id)
            REFERENCES t_knowledge_base (id)
            ON DELETE RESTRICT;
    END IF;
END
$$;

DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1
        FROM pg_constraint
        WHERE conname = 'chk_knowledge_base_not_self_parent'
          AND conrelid = 't_knowledge_base'::regclass
    ) THEN
        ALTER TABLE t_knowledge_base
            ADD CONSTRAINT chk_knowledge_base_not_self_parent
            CHECK (parent_id IS NULL OR parent_id <> id);
    END IF;
END
$$;

CREATE INDEX IF NOT EXISTS idx_knowledge_base_parent_id
    ON t_knowledge_base (parent_id);

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

DROP TRIGGER IF EXISTS trg_prevent_knowledge_base_cycle ON t_knowledge_base;

CREATE TRIGGER trg_prevent_knowledge_base_cycle
BEFORE INSERT OR UPDATE OF parent_id ON t_knowledge_base
FOR EACH ROW
EXECUTE FUNCTION prevent_knowledge_base_cycle();

COMMIT;
