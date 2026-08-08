package com.example.knowledge.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.knowledge.domain.KnowledgeChunk;
import com.example.knowledge.domain.KnowledgeDocument;
import com.example.knowledge.domain.SearchHit;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.h2.jdbcx.JdbcDataSource;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.ai.document.Document;
import org.springframework.ai.embedding.Embedding;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.embedding.EmbeddingRequest;
import org.springframework.ai.embedding.EmbeddingResponse;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.ai.vectorstore.filter.Filter;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;

class JdbcKnowledgeRepositoryTest {

    private JdbcTemplate jdbcTemplate;
    private RecordingVectorStore vectorStore;
    private RecordingEmbeddingModel embeddingModel;
    private JdbcKnowledgeRepository repository;

    @BeforeEach
    void setUp() {
        JdbcDataSource dataSource = new JdbcDataSource();
        dataSource.setURL("jdbc:h2:mem:" + UUID.randomUUID() + ";MODE=PostgreSQL;DB_CLOSE_DELAY=-1");
        jdbcTemplate = new JdbcTemplate(dataSource);
        jdbcTemplate.execute("""
                CREATE TABLE t_knowledge_base (
                    id BIGSERIAL PRIMARY KEY,
                    name VARCHAR(200) NOT NULL,
                    description TEXT,
                    create_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
                    update_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
                    deleted INTEGER NOT NULL DEFAULT 0
                )
                """);
        jdbcTemplate.execute("""
                CREATE TABLE t_knowledge_document (
                    id BIGSERIAL PRIMARY KEY,
                    knowledge_base_id BIGINT NOT NULL,
                    title VARCHAR(500) NOT NULL,
                    source_type VARCHAR(50),
                    content TEXT,
                    status VARCHAR(30) NOT NULL,
                    create_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
                    update_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
                    deleted INTEGER NOT NULL DEFAULT 0
                )
                """);
        jdbcTemplate.execute("""
                CREATE TABLE t_document_chunk (
                    id BIGSERIAL PRIMARY KEY,
                    document_id BIGINT NOT NULL,
                    chunk_index INTEGER NOT NULL,
                    content TEXT NOT NULL,
                    vector_document_id VARCHAR(100),
                    create_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
                    update_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
                    deleted INTEGER NOT NULL DEFAULT 0
                )
                """);
        jdbcTemplate.update("INSERT INTO t_knowledge_base (name) VALUES (?)", "Java Knowledge");
        vectorStore = new RecordingVectorStore();
        embeddingModel = new RecordingEmbeddingModel();
        repository = new JdbcKnowledgeRepository(jdbcTemplate, vectorStore, embeddingModel, 2);
    }

    @Test
    void shouldSaveDocumentChunksAndVectorDocuments() {
        KnowledgeDocument document =
                new KnowledgeDocument(1L, "Spring AI", "TEXT", "complete content");
        List<KnowledgeChunk> chunks = List.of(
                new KnowledgeChunk("vector-1", "first chunk", 0),
                new KnowledgeChunk("vector-2", "second chunk", 1),
                new KnowledgeChunk("vector-3", "third chunk", 2));

        Long documentId = repository.save(document, chunks);

        Map<String, Object> storedDocument = jdbcTemplate.queryForMap("""
                SELECT knowledge_base_id, title, source_type, content, status
                FROM t_knowledge_document
                WHERE id = ?
                """, documentId);
        assertThat(storedDocument)
                .containsEntry("KNOWLEDGE_BASE_ID", 1L)
                .containsEntry("TITLE", "Spring AI")
                .containsEntry("SOURCE_TYPE", "TEXT")
                .containsEntry("CONTENT", "complete content")
                .containsEntry("STATUS", "COMPLETED");

        List<Map<String, Object>> storedChunks = jdbcTemplate.queryForList("""
                SELECT document_id, chunk_index, content, vector_document_id
                FROM t_document_chunk
                ORDER BY chunk_index
                """);
        assertThat(storedChunks).hasSize(3);
        assertThat(storedChunks.get(0))
                .containsEntry("DOCUMENT_ID", documentId)
                .containsEntry("CHUNK_INDEX", 0)
                .containsEntry("CONTENT", "first chunk")
                .containsEntry("VECTOR_DOCUMENT_ID", "vector-1");

        assertThat(vectorStore.addedDocuments).hasSize(3);
        Document firstVectorDocument = vectorStore.addedDocuments.get(0);
        assertThat(firstVectorDocument.getId()).isEqualTo("vector-1");
        assertThat(firstVectorDocument.getMetadata())
                .containsEntry("knowledgeBaseId", 1L)
                .containsEntry("documentId", documentId)
                .containsEntry("title", "Spring AI")
                .containsEntry("chunkIndex", 0);
    }

    @Test
    void shouldCheckKnowledgeBase() {
        assertThat(repository.existsKnowledgeBase(1L)).isTrue();
        assertThat(repository.existsKnowledgeBase(99L)).isFalse();
    }

    @Test
    void shouldIgnoreSoftDeletedKnowledgeBase() {
        jdbcTemplate.update("UPDATE t_knowledge_base SET deleted = 1 WHERE id = ?", 1L);

        assertThat(repository.existsKnowledgeBase(1L)).isFalse();
    }

    @Test
    void shouldNotCompleteSoftDeletedDocument() {
        vectorStore.onAdd = () -> jdbcTemplate.update(
                "UPDATE t_knowledge_document SET deleted = 1");
        KnowledgeDocument document =
                new KnowledgeDocument(1L, "Temporary document", "TEXT", "temporary content");

        Long documentId = repository.save(
                document,
                List.of(new KnowledgeChunk("temporary-vector", "temporary content", 0)));

        Map<String, Object> storedDocument = jdbcTemplate.queryForMap(
                "SELECT status, deleted FROM t_knowledge_document WHERE id = ?", documentId);
        assertThat(storedDocument)
                .containsEntry("STATUS", "PROCESSING")
                .containsEntry("DELETED", 1);
    }

    @Test
    void shouldFilterSpecifiedKnowledgeBaseWithItsPersistedThreshold() {
        RecordingSearchJdbcTemplate searchJdbcTemplate = new RecordingSearchJdbcTemplate();
        SearchHit expectedHit = new SearchHit("Spring AI", "Spring AI content", 0.92);
        searchJdbcTemplate.results = List.of(expectedHit);
        embeddingModel.output = new float[] {0.25F, -0.5F};
        JdbcKnowledgeRepository searchRepository =
                new JdbcKnowledgeRepository(searchJdbcTemplate, vectorStore, embeddingModel, 2);

        List<SearchHit> hits = searchRepository.search(1L, "Spring AI", 3);

        assertThat(hits).containsExactly(expectedHit);
        assertThat(searchJdbcTemplate.sql)
                .contains(
                        "WITH RECURSIVE",
                        "child.parent_id = parent.id",
                        "kb.id IN (SELECT id FROM knowledge_scope)",
                        "kb.deleted = 0",
                        "v.deleted = 0",
                        "document.deleted = 0",
                        "kb.similarity_threshold")
                .doesNotContain("similarityThreshold");
        assertThat(searchJdbcTemplate.arguments)
                .containsExactly("[0.25,-0.5]", 1L, 1L, 3);
        assertThat(embeddingModel.query).isEqualTo("Spring AI");
    }

    @Test
    void shouldApplyEachKnowledgeBaseThresholdWhenSearchingAllKnowledgeBases() {
        RecordingSearchJdbcTemplate searchJdbcTemplate = new RecordingSearchJdbcTemplate();
        embeddingModel.output = new float[] {0.1F, 0.2F};
        JdbcKnowledgeRepository searchRepository =
                new JdbcKnowledgeRepository(searchJdbcTemplate, vectorStore, embeddingModel, 2);

        searchRepository.search(null, "future tasks", 10);

        assertThat(searchJdbcTemplate.sql)
                .contains(
                        "WITH RECURSIVE",
                        "child.parent_id = parent.id",
                        "kb.id IN (SELECT id FROM knowledge_scope)",
                        "kb.deleted = 0",
                        "v.deleted = 0",
                        "document.deleted = 0",
                        "kb.similarity_threshold");
        assertThat(searchJdbcTemplate.arguments)
                .containsExactly("[0.1,0.2]", null, null, 10);
    }

    private static final class RecordingVectorStore implements VectorStore {

        private final List<Document> addedDocuments = new ArrayList<>();
        private Runnable onAdd = () -> { };

        @Override
        public void add(List<Document> documents) {
            addedDocuments.addAll(documents);
            onAdd.run();
        }

        @Override
        public void delete(List<String> idList) {
        }

        @Override
        public void delete(Filter.Expression filterExpression) {
        }

        @Override
        public List<Document> similaritySearch(SearchRequest request) {
            return List.of();
        }

    }

    private static final class RecordingSearchJdbcTemplate extends JdbcTemplate {

        private String sql;
        private Object[] arguments;
        private List<SearchHit> results = List.of();

        @Override
        @SuppressWarnings("unchecked")
        public <T> List<T> query(String sql, RowMapper<T> rowMapper, Object... args) {
            this.sql = sql;
            this.arguments = args;
            return (List<T>) results;
        }
    }

    private static final class RecordingEmbeddingModel implements EmbeddingModel {

        private float[] output = new float[] {0.0F};
        private String query;

        @Override
        public EmbeddingResponse call(EmbeddingRequest request) {
            query = request.getInstructions().get(0);
            return new EmbeddingResponse(List.of(new Embedding(output, 0)));
        }

        @Override
        public float[] embed(Document document) {
            return output;
        }
    }
}
