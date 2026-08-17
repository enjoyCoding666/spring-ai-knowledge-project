package com.example.knowledge.web;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.example.knowledge.application.DeepSeekChatService;
import com.example.knowledge.port.DeepSeekChatPort;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

class DeepSeekControllerTest {

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        DeepSeekChatPort port = message -> "DeepSeek 的回答";
        mockMvc = MockMvcBuilders
                .standaloneSetup(new DeepSeekController(new DeepSeekChatService(port)))
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @Test
    void shouldReturnDeepSeekAnswer() throws Exception {
        mockMvc.perform(post("/api/deepseek/chat")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"message":"你好"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.message").value("success"))
                .andExpect(jsonPath("$.data.answer").value("DeepSeek 的回答"));
    }

    @Test
    void shouldRejectBlankMessage() throws Exception {
        mockMvc.perform(post("/api/deepseek/chat")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"message":""}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(400))
                .andExpect(jsonPath("$.message").value("Message must not be blank"))
                .andExpect(jsonPath("$.data").isEmpty());
    }
}
