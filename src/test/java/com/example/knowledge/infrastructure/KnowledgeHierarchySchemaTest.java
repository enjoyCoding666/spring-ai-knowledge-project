package com.example.knowledge.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class KnowledgeHierarchySchemaTest {

    private static final Path INIT_SQL = Path.of("sql/init.sql");

    @Test
    void shouldDefineKnowledgeHierarchyIntegrityInInitSql() throws IOException {
        assertHierarchySchema(Files.readString(INIT_SQL));
    }

    private void assertHierarchySchema(String sql) {
        assertThat(sql)
                .contains(
                        "parent_id",
                        "REFERENCES t_knowledge_base (id)",
                        "ON DELETE RESTRICT",
                        "idx_knowledge_base_parent_id",
                        "prevent_knowledge_base_cycle",
                        "WITH RECURSIVE");
    }
}
