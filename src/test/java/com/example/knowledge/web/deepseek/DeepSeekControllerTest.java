package com.example.knowledge.web.deepseek;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.example.knowledge.service.deepseek.DeepSeekChatService;
import com.example.knowledge.thirdparty.DeepSeekChatClient;
import com.example.knowledge.web.GlobalExceptionHandler;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

class DeepSeekControllerTest {

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        DeepSeekChatClient client = message -> "DeepSeek 的回答";
        mockMvc = MockMvcBuilders
                .standaloneSetup(new DeepSeekController(new DeepSeekChatService(client)))
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
