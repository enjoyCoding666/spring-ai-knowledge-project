package com.example.knowledge.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class AuditSchemaTest {

    private static final Path INIT_SQL = Path.of("sql/init.sql");
    private static final Path MIGRATION_SQL = Path.of("sql/migrate_add_audit_fields.sql");

    private String initSql;
    private String migrationSql;

    @BeforeEach
    void setUp() throws IOException {
        initSql = Files.readString(INIT_SQL);
        migrationSql = Files.exists(MIGRATION_SQL) ? Files.readString(MIGRATION_SQL) : "";
    }

    @Test
    void shouldDefineAuditColumnsAndDeletedConstraint() {
        assertThat(MIGRATION_SQL).exists();
        assertAuditColumns(initSql);
        assertAuditColumns(migrationSql);
        assertThat(migrationSql).contains("RENAME COLUMN created_at TO create_time");
    }

    @Test
    void shouldAutomaticallyMaintainUpdateTimeForEveryTable() {
        assertThat(MIGRATION_SQL).exists();
        assertUpdateTimeTriggers(initSql);
        assertUpdateTimeTriggers(migrationSql);
    }

    @Test
    void shouldPreventSoftDeletingParentWithActiveChildren() {
        assertThat(MIGRATION_SQL).exists();
        assertThat(initSql)
                .contains("prevent_parent_soft_delete", "trg_prevent_parent_soft_delete");
        assertThat(migrationSql)
                .contains("prevent_parent_soft_delete", "trg_prevent_parent_soft_delete");
    }

    @Test
    void shouldUseActiveRecordIndexes() {
        assertThat(MIGRATION_SQL).exists();
        assertThat(initSql).contains("WHERE deleted = 0");
        assertThat(migrationSql).contains("WHERE deleted = 0");
    }

    private void assertAuditColumns(String sql) {
        assertThat(sql)
                .contains(
                        "create_time",
                        "update_time",
                        "deleted",
                        "CHECK (deleted IN (0, 1))");
    }

    private void assertUpdateTimeTriggers(String sql) {
        assertThat(sql)
                .contains(
                        "set_update_time",
                        "trg_knowledge_base_update_time",
                        "trg_knowledge_document_update_time",
                        "trg_document_chunk_update_time",
                        "trg_vector_store_update_time");
    }
}
