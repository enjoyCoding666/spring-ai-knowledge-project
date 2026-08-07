package com.example.knowledge.web;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.asyncDispatch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.request;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.example.knowledge.application.ChatUseCase;
import com.example.knowledge.domain.ChatAnswer;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

class ChatControllerTest {

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        ChatUseCase chatUseCase = (knowledgeBaseId, question) -> CompletableFuture.completedFuture(
                new ChatAnswer("Spring AI 通过向量检索实现 RAG。", List.of("Spring AI")));
        mockMvc = MockMvcBuilders
                .standaloneSetup(new ChatController(chatUseCase))
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @Test
    void shouldReturnRagAnswer() throws Exception {
        MvcResult result = mockMvc.perform(post("/api/chat")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"knowledgeBaseId":7,"question":"如何实现 RAG？"}
                                """))
                .andExpect(request().asyncStarted())
                .andReturn();

        mockMvc.perform(asyncDispatch(result))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.message").value("success"))
                .andExpect(jsonPath("$.data.answer").value("Spring AI 通过向量检索实现 RAG。"))
                .andExpect(jsonPath("$.data.sources[0]").value("Spring AI"));
    }
}
