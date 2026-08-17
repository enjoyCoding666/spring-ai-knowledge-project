package com.example.knowledge.application;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.knowledge.port.DeepSeekChatPort;
import org.junit.jupiter.api.Test;

class DeepSeekChatServiceTest {

    @Test
    void shouldDelegateMessageToPort() {
        RecordingDeepSeekChatPort port = new RecordingDeepSeekChatPort();
        DeepSeekChatService deepSeekChatService = new DeepSeekChatService(port);

        String answer = deepSeekChatService.chat("介绍一下 Spring AI");

        assertThat(answer).isEqualTo("answer");
        assertThat(port.message).isEqualTo("介绍一下 Spring AI");
    }

    private static final class RecordingDeepSeekChatPort implements DeepSeekChatPort {

        private String message;

        @Override
        public String chat(String message) {
            this.message = message;
            return "answer";
        }
    }
}
