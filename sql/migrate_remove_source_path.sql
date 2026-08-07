BEGIN;

ALTER TABLE t_knowledge_document
    DROP COLUMN IF EXISTS source_path;

COMMIT;
