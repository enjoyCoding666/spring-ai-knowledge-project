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
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.ai.vectorstore.filter.Filter;
import org.springframework.jdbc.core.JdbcTemplate;

class JdbcKnowledgeRepositoryTest {

    private JdbcTemplate jdbcTemplate;
    private RecordingVectorStore vectorStore;
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
                    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
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
                    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
                )
                """);
        jdbcTemplate.execute("""
                CREATE TABLE t_document_chunk (
                    id BIGSERIAL PRIMARY KEY,
                    document_id BIGINT NOT NULL,
                    chunk_index INTEGER NOT NULL,
                    content TEXT NOT NULL,
                    vector_document_id VARCHAR(100),
                    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
                )
                """);
        jdbcTemplate.update("INSERT INTO t_knowledge_base (name) VALUES (?)", "Java Knowledge");
        vectorStore = new RecordingVectorStore();
        repository = new JdbcKnowledgeRepository(jdbcTemplate, vectorStore, 2);
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
    void shouldCheckKnowledgeBaseAndRestrictVectorSearch() {
        vectorStore.searchResults = List.of(Document.builder()
                .id("vector-1")
                .text("Spring AI content")
                .metadata("title", "Spring AI")
                .score(0.92)
                .build());

        assertThat(repository.existsKnowledgeBase(1L)).isTrue();
        assertThat(repository.existsKnowledgeBase(99L)).isFalse();

        List<SearchHit> hits = repository.search(1L, "Spring AI", 3);

        assertThat(hits).containsExactly(new SearchHit("Spring AI", "Spring AI content", 0.92));
        assertThat(vectorStore.searchRequest.getQuery()).isEqualTo("Spring AI");
        assertThat(vectorStore.searchRequest.getTopK()).isEqualTo(3);
        assertThat(vectorStore.searchRequest.getSimilarityThreshold()).isEqualTo(0.5);
        assertThat(vectorStore.searchRequest.getFilterExpression().toString())
                .contains("knowledgeBaseId", "1");
    }

    @Test
    void shouldSearchAllKnowledgeBasesWithoutVectorFilter() {
        repository.search(null, "Spring AI", 3);

        assertThat(vectorStore.searchRequest.getFilterExpression()).isNull();
    }

    @Test
    void shouldUseConfiguredSimilarityThreshold() {
        double configuredThreshold = 0.65;
        JdbcKnowledgeRepository configuredRepository =
                new JdbcKnowledgeRepository(jdbcTemplate, vectorStore, 2, configuredThreshold);

        configuredRepository.search(null, "Spring AI", 3);

        assertThat(vectorStore.searchRequest.getSimilarityThreshold())
                .isEqualTo(configuredThreshold);
    }

    private static final class RecordingVectorStore implements VectorStore {

        private final List<Document> addedDocuments = new ArrayList<>();
        private List<Document> searchResults = List.of();
        private SearchRequest searchRequest;

        @Override
        public void add(List<Document> documents) {
            addedDocuments.addAll(documents);
        }

        @Override
        public void delete(List<String> idList) {
        }

        @Override
        public void delete(Filter.Expression filterExpression) {
        }

        @Override
        public List<Document> similaritySearch(SearchRequest request) {
            searchRequest = request;
            return searchResults;
        }
    }
}
