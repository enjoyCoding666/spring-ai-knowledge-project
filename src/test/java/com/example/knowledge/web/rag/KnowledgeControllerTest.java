package com.example.knowledge.web.rag;

import static com.example.knowledge.TestComponents.knowledgeBaseService;
import static com.example.knowledge.TestComponents.knowledgeSearchService;
import static com.example.knowledge.TestComponents.plainTextFileReader;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.asyncDispatch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.request;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.example.knowledge.service.rag.KnowledgeBaseService;
import com.example.knowledge.service.rag.KnowledgeImportUseCase;
import com.example.knowledge.service.rag.KnowledgeSearchService;
import com.example.knowledge.service.rag.PassthroughReranker;
import com.example.knowledge.service.rag.PlainTextFileReader;
import com.example.knowledge.domain.KnowledgeBase;
import com.example.knowledge.domain.KnowledgeChunk;
import com.example.knowledge.domain.KnowledgeDocument;
import com.example.knowledge.domain.KnowledgeImportResult;
import com.example.knowledge.domain.SearchHit;
import com.example.knowledge.dao.KnowledgeDao;
import com.example.knowledge.web.GlobalExceptionHandler;
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
import org.springframework.test.util.ReflectionTestUtils;

class KnowledgeControllerTest {

    private static final long MAX_FILE_SIZE = 32L;

    private MockMvc mockMvc;
    private StubKnowledgeDao repository;
    private KnowledgeDocument importedDocument;

    @BeforeEach
    void setUp() {
        KnowledgeImportUseCase importer = document -> {
            importedDocument = document;
            return CompletableFuture.completedFuture(new KnowledgeImportResult(42L, 2));
        };
        repository = new StubKnowledgeDao();
        KnowledgeSearchService searchService =
                knowledgeSearchService(repository, new PassthroughReranker(), 30);
        KnowledgeBaseService knowledgeBaseService = knowledgeBaseService(repository);
        KnowledgeController controller = new KnowledgeController();
        ReflectionTestUtils.setField(controller, "knowledgeImporter", importer);
        ReflectionTestUtils.setField(controller, "knowledgeSearchService", searchService);
        ReflectionTestUtils.setField(
                controller, "plainTextFileReader", plainTextFileReader(MAX_FILE_SIZE));
        ReflectionTestUtils.setField(controller, "knowledgeBaseService", knowledgeBaseService);
        mockMvc = MockMvcBuilders
                .standaloneSetup(controller)
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @Test
    void shouldCreateKnowledgeBase() throws Exception {
        mockMvc.perform(post("/api/knowledge/bases")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "name":"Java Knowledge",
                                  "description":"Java related guides",
                                  "parentId":3,
                                  "similarityThreshold":0.6
                                }
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.message").value("success"))
                .andExpect(jsonPath("$.data.knowledgeBaseId").value(10));

        assertThat(repository.createdKnowledgeBase.name()).isEqualTo("Java Knowledge");
        assertThat(repository.createdKnowledgeBase.description()).isEqualTo("Java related guides");
        assertThat(repository.createdKnowledgeBase.parentId()).isEqualTo(3L);
        assertThat(repository.createdKnowledgeBase.similarityThreshold()).isEqualTo(0.6);
    }

    @Test
    void shouldRejectBlankKnowledgeBaseName() throws Exception {
        mockMvc.perform(post("/api/knowledge/bases")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"","description":"desc"}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(400))
                .andExpect(jsonPath("$.message").value("Name must not be blank"))
                .andExpect(jsonPath("$.data").isEmpty());
    }

    @Test
    void shouldRejectUnknownParentKnowledgeBase() throws Exception {
        repository.knowledgeBaseExists = false;

        mockMvc.perform(post("/api/knowledge/bases")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"Child Knowledge","parentId":99}
                                """))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value(404))
                .andExpect(jsonPath("$.message").value("Knowledge base does not exist: 99"))
                .andExpect(jsonPath("$.data").isEmpty());
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

    @Test
    void shouldReturnNotFoundForUnknownEndpoint() throws Exception {
        mockMvc.perform(post("/api/no-such-endpoint")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value(404))
                .andExpect(jsonPath("$.message").value("Resource not found"))
                .andExpect(jsonPath("$.data").isEmpty());
    }

    private static final class StubKnowledgeDao implements KnowledgeDao {

        private Long searchedKnowledgeBaseId;
        private Integer searchedLimit;
        private KnowledgeBase createdKnowledgeBase;
        private boolean knowledgeBaseExists = true;

        @Override
        public boolean existsKnowledgeBase(Long knowledgeBaseId) {
            return knowledgeBaseExists;
        }

        @Override
        public Long createKnowledgeBase(KnowledgeBase knowledgeBase) {
            createdKnowledgeBase = knowledgeBase;
            return 10L;
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
