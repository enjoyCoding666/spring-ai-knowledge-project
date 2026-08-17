package com.example.knowledge.service.deepseek;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.knowledge.thirdparty.DeepSeekChatClient;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

class DeepSeekChatServiceTest {

    @Test
    void shouldDelegateMessageToPort() {
        RecordingDeepSeekChatClient client = new RecordingDeepSeekChatClient();
        DeepSeekChatService deepSeekChatService = new DeepSeekChatService();
        ReflectionTestUtils.setField(deepSeekChatService, "deepSeekChatClient", client);

        String answer = deepSeekChatService.chat("介绍一下 Spring AI");

        assertThat(answer).isEqualTo("answer");
        assertThat(client.message).isEqualTo("介绍一下 Spring AI");
    }

    private static final class RecordingDeepSeekChatClient implements DeepSeekChatClient {

        private String message;

        @Override
        public String chat(String message) {
            this.message = message;
            return "answer";
        }
    }
}
