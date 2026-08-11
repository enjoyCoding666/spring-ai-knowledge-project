package com.example.knowledge.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.asyncDispatch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.request;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.example.knowledge.application.KnowledgeImportUseCase;
import com.example.knowledge.application.KnowledgeSearchService;
import com.example.knowledge.application.PassthroughReranker;
import com.example.knowledge.application.PlainTextFileReader;
import com.example.knowledge.domain.KnowledgeChunk;
import com.example.knowledge.domain.KnowledgeDocument;
import com.example.knowledge.domain.KnowledgeImportResult;
import com.example.knowledge.domain.SearchHit;
import com.example.knowledge.port.KnowledgeRepository;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.stream.IntStream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

class KnowledgeControllerTest {

    private static final long MAX_FILE_SIZE = 32L;

    private MockMvc mockMvc;
    private StubKnowledgeRepository repository;
    private KnowledgeDocument importedDocument;

    @BeforeEach
    void setUp() {
        KnowledgeImportUseCase importer = document -> {
            importedDocument = document;
            return CompletableFuture.completedFuture(new KnowledgeImportResult(42L, 2));
        };
        repository = new StubKnowledgeRepository();
        KnowledgeSearchService searchService =
                new KnowledgeSearchService(repository, new PassthroughReranker(), 30);
        mockMvc = MockMvcBuilders
                .standaloneSetup(new KnowledgeController(
                        importer, searchService, new PlainTextFileReader(MAX_FILE_SIZE)))
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @Test
    void shouldImportKnowledgeDocument() throws Exception {
        MvcResult result = mockMvc.perform(post("/api/knowledge/documents")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "knowledgeBaseId":7,
                                  "title":"Spring AI",
                                  "sourceType":"TEXT",
                                  "content":"Spring AI supports RAG."
                                }
                                """))
                .andExpect(request().asyncStarted())
                .andReturn();

        mockMvc.perform(asyncDispatch(result))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.message").value("success"))
                .andExpect(jsonPath("$.data.documentId").value(42))
                .andExpect(jsonPath("$.data.chunkCount").value(2));

    }

    @Test
    void shouldRejectEmptyUploadedFile() throws Exception {
        MockMultipartFile file = new MockMultipartFile(
                "file", "empty.txt", MediaType.TEXT_PLAIN_VALUE, new byte[0]);

        mockMvc.perform(multipart("/api/knowledge/files")
                        .file(file)
                        .param("knowledgeBaseId", "7")
                        .param("title", "Empty"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(400))
                .andExpect(jsonPath("$.message").value("File must not be empty"))
                .andExpect(jsonPath("$.data").isEmpty());
    }

    @Test
    void shouldImportUtf8TextFile() throws Exception {
        MockMultipartFile file = new MockMultipartFile(
                "file",
                "spring-ai.txt",
                MediaType.TEXT_PLAIN_VALUE,
                "Spring AI supports RAG.".getBytes(StandardCharsets.UTF_8));

        MvcResult result = mockMvc.perform(multipart("/api/knowledge/files")
                        .file(file)
                        .param("knowledgeBaseId", "7")
                        .param("title", "Spring AI")
                        .param("sourceType", "TEXT"))
                .andExpect(request().asyncStarted())
                .andReturn();

        mockMvc.perform(asyncDispatch(result))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.message").value("success"))
                .andExpect(jsonPath("$.data.documentId").value(42))
                .andExpect(jsonPath("$.data.chunkCount").value(2));

        assertThat(importedDocument.knowledgeBaseId()).isEqualTo(7L);
        assertThat(importedDocument.title()).isEqualTo("Spring AI");
        assertThat(importedDocument.sourceType()).isEqualTo("TEXT");
        assertThat(importedDocument.content()).isEqualTo("Spring AI supports RAG.");
    }

    @Test
    void shouldRejectBlankDocumentTitle() throws Exception {
        mockMvc.perform(post("/api/knowledge/documents")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"knowledgeBaseId":7,"title":"","content":"content"}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(400))
                .andExpect(jsonPath("$.message").value("Title must not be blank"))
                .andExpect(jsonPath("$.data").isEmpty());
    }

    @Test
    void shouldSearchKnowledge() throws Exception {
        mockMvc.perform(post("/api/knowledge/search")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"knowledgeBaseId":7,"query":"RAG","limit":3}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.message").value("success"))
                .andExpect(jsonPath("$.data[0].title").value("Spring AI"))
                .andExpect(jsonPath("$.data[0].content").value("RAG content"))
                .andExpect(jsonPath("$.data[0].score").value(0.91))
                .andExpect(jsonPath("$.data[0].scoreSource").value("PGVECTOR"))
                .andExpect(header().string("X-Rerank-Source", "PGVECTOR"));
    }

    @Test
    void shouldSearchAllKnowledgeBasesWhenIdIsOmitted() throws Exception {
        mockMvc.perform(post("/api/knowledge/search")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"query":"RAG","limit":3}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0));

        assertThat(repository.searchedKnowledgeBaseId).isNull();
    }

    @Test
    void shouldUseDefaultLimitWhenLimitIsOmitted() throws Exception {
        mockMvc.perform(post("/api/knowledge/search")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"query":"RAG"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(5));

        assertThat(repository.searchedLimit).isEqualTo(30);
    }

    private static final class StubKnowledgeRepository implements KnowledgeRepository {

        private Long searchedKnowledgeBaseId;
        private Integer searchedLimit;

        @Override
        public boolean existsKnowledgeBase(Long knowledgeBaseId) {
            return true;
        }

        @Override
        public Long save(KnowledgeDocument document, List<KnowledgeChunk> chunks) {
            return 1L;
        }

        @Override
        public List<SearchHit> search(Long knowledgeBaseId, String query, int limit) {
            searchedKnowledgeBaseId = knowledgeBaseId;
            searchedLimit = limit;
            return IntStream.range(0, 6)
                    .mapToObj(index -> new SearchHit(
                            index == 0 ? "Spring AI" : "Guide " + index,
                            index == 0 ? "RAG content" : "Candidate " + index,
                            0.91 - index * 0.05))
                    .toList();
        }
    }
}
