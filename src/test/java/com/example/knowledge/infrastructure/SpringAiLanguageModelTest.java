package com.example.knowledge.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.prompt.Prompt;

class SpringAiLanguageModelTest {

    @Test
    void shouldCallChatClientWithSystemPromptAndQuestion() {
        RecordingChatModel chatModel = new RecordingChatModel();
        SpringAiLanguageModel languageModel = new SpringAiLanguageModel(ChatClient.create(chatModel));

        String answer = languageModel.generate("system prompt", "question");

        assertThat(answer).isEqualTo("answer");
        assertThat(chatModel.prompt.getSystemMessage().getText()).isEqualTo("system prompt");
        assertThat(chatModel.prompt.getUserMessage().getText()).isEqualTo("question");
    }

    private static final class RecordingChatModel implements ChatModel {

        private Prompt prompt;

        @Override
        public ChatResponse call(Prompt prompt) {
            this.prompt = prompt;
            return new ChatResponse(List.of(new Generation(new AssistantMessage("answer"))));
        }
    }
}
