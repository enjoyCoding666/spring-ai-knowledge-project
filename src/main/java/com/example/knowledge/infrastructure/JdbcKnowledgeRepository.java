package com.example.knowledge.infrastructure;

import com.example.knowledge.domain.KnowledgeChunk;
import com.example.knowledge.domain.KnowledgeDocument;
import com.example.knowledge.domain.SearchHit;
import com.example.knowledge.port.KnowledgeRepository;
import java.sql.PreparedStatement;
import java.util.List;
import org.springframework.ai.document.Document;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.transaction.annotation.Transactional;

public class JdbcKnowledgeRepository implements KnowledgeRepository {

    private static final String KNOWLEDGE_BASE_ID_METADATA = "knowledgeBaseId";
    private static final String DOCUMENT_ID_METADATA = "documentId";
    private static final String TITLE_METADATA = "title";
    private static final String CHUNK_INDEX_METADATA = "chunkIndex";
    private static final String INSERT_DOCUMENT_SQL = """
            INSERT INTO t_knowledge_document
                (knowledge_base_id, title, source_type, content, status)
            VALUES (?, ?, ?, ?, ?)
            """;
    private static final String INSERT_CHUNK_SQL = """
            INSERT INTO t_document_chunk
                (document_id, chunk_index, content, vector_document_id)
            VALUES (?, ?, ?, ?)
            """;
    private static final String COMPLETE_DOCUMENT_SQL = """
            UPDATE t_knowledge_document SET status = ? WHERE id = ?
            """;
    private static final String EXISTS_KNOWLEDGE_BASE_SQL = """
            SELECT COUNT(1) FROM t_knowledge_base WHERE id = ?
            """;
    private static final String SEARCH_SQL = """
            WITH query_vector AS (
                SELECT CAST(? AS vector) AS embedding
            )
            SELECT v.metadata ->> 'title' AS title,
                   v.content,
                   1 - (v.embedding <=> query_vector.embedding) AS score
            FROM t_vector_store v
            JOIN t_knowledge_base kb
              ON kb.id = CAST(v.metadata ->> 'knowledgeBaseId' AS BIGINT)
            CROSS JOIN query_vector
            WHERE (CAST(? AS BIGINT) IS NULL OR kb.id = CAST(? AS BIGINT))
              AND 1 - (v.embedding <=> query_vector.embedding)
                  >= kb.similarity_threshold
            ORDER BY v.embedding <=> query_vector.embedding
            LIMIT ?
            """;
    private static final RowMapper<Long> COUNT_ROW_MAPPER =
            (resultSet, rowNumber) -> resultSet.getLong(1);
    private static final RowMapper<SearchHit> SEARCH_HIT_ROW_MAPPER =
            (resultSet, rowNumber) -> new SearchHit(
                    resultSet.getString("title"),
                    resultSet.getString("content"),
                    resultSet.getDouble("score"));

    private final JdbcTemplate jdbcTemplate;
    private final VectorStore vectorStore;
    private final EmbeddingModel embeddingModel;
    private final int batchSize;

    public JdbcKnowledgeRepository(
            JdbcTemplate jdbcTemplate,
            VectorStore vectorStore,
            EmbeddingModel embeddingModel,
            int batchSize) {
        if (batchSize <= 0) {
            throw new IllegalArgumentException("Batch size must be greater than zero");
        }
        this.jdbcTemplate = jdbcTemplate;
        this.vectorStore = vectorStore;
        this.embeddingModel = embeddingModel;
        this.batchSize = batchSize;
    }

    @Override
    public boolean existsKnowledgeBase(Long knowledgeBaseId) {
        Long count = jdbcTemplate.queryForObject(
                EXISTS_KNOWLEDGE_BASE_SQL, COUNT_ROW_MAPPER, knowledgeBaseId);
        return count != null && count > 0;
    }

    /**
     * 在同一业务事务中保存原文、分块和向量记录。
     */
    @Override
    @Transactional
    public Long save(KnowledgeDocument document, List<KnowledgeChunk> chunks) {
        Long documentId = insertDocument(document);
        vectorStore.add(toVectorDocuments(document, documentId, chunks));
        insertChunks(documentId, chunks);
        jdbcTemplate.update(COMPLETE_DOCUMENT_SQL, DocumentStatus.COMPLETED.name(), documentId);
        return documentId;
    }

    @Override
    public List<SearchHit> search(Long knowledgeBaseId, String query, int limit) {
        String queryVector = toPgVector(embeddingModel.embed(query));
        return jdbcTemplate.query(
                SEARCH_SQL,
                SEARCH_HIT_ROW_MAPPER,
                queryVector,
                knowledgeBaseId,
                knowledgeBaseId,
                limit);
    }

    private Long insertDocument(KnowledgeDocument document) {
        KeyHolder keyHolder = new GeneratedKeyHolder();
        jdbcTemplate.update(connection -> {
            PreparedStatement statement = connection.prepareStatement(
                    INSERT_DOCUMENT_SQL, new String[] {"id"});
            statement.setLong(1, document.knowledgeBaseId());
            statement.setString(2, document.title());
            statement.setString(3, document.sourceType());
            statement.setString(4, document.content());
            statement.setString(5, DocumentStatus.PROCESSING.name());
            return statement;
        }, keyHolder);
        Number generatedKey = keyHolder.getKey();
        if (generatedKey == null) {
            throw new IllegalStateException("Database did not return a document id");
        }
        return generatedKey.longValue();
    }

    private List<Document> toVectorDocuments(
            KnowledgeDocument sourceDocument,
            Long documentId,
            List<KnowledgeChunk> chunks) {
        return chunks.stream()
                .map(chunk -> Document.builder()
                        .id(chunk.vectorDocumentId())
                        .text(chunk.content())
                        .metadata(KNOWLEDGE_BASE_ID_METADATA, sourceDocument.knowledgeBaseId())
                        .metadata(DOCUMENT_ID_METADATA, documentId)
                        .metadata(TITLE_METADATA, sourceDocument.title())
                        .metadata(CHUNK_INDEX_METADATA, chunk.chunkIndex())
                        .build())
                .toList();
    }

    private void insertChunks(Long documentId, List<KnowledgeChunk> chunks) {
        for (int start = 0; start < chunks.size(); start += batchSize) {
            int end = Math.min(start + batchSize, chunks.size());
            List<KnowledgeChunk> batch = chunks.subList(start, end);
            jdbcTemplate.batchUpdate(INSERT_CHUNK_SQL, batch, batchSize, (statement, chunk) -> {
                statement.setLong(1, documentId);
                statement.setInt(2, chunk.chunkIndex());
                statement.setString(3, chunk.content());
                statement.setString(4, chunk.vectorDocumentId());
            });
        }
    }

    /**
     * 将模型向量转换为 PgVector 可识别的文本格式。
     */
    private String toPgVector(float[] embedding) {
        StringBuilder vector = new StringBuilder("[");
        for (int index = 0; index < embedding.length; index++) {
            if (index > 0) {
                vector.append(',');
            }
            vector.append(embedding[index]);
        }
        return vector.append(']').toString();
    }

    private enum DocumentStatus {
        PROCESSING,
        COMPLETED
    }
}
