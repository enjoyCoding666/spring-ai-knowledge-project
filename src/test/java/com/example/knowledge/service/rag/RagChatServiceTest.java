package com.example.knowledge.service.rag;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.knowledge.domain.ChatAnswer;
import com.example.knowledge.domain.KnowledgeBase;
import com.example.knowledge.domain.KnowledgeChunk;
import com.example.knowledge.domain.KnowledgeDocument;
import com.example.knowledge.domain.SearchHit;
import com.example.knowledge.dao.KnowledgeDao;
import com.example.knowledge.thirdparty.LanguageModel;
import java.util.List;
import org.junit.jupiter.api.Test;

class RagChatServiceTest {

    @Test
    void shouldSendRetrievedKnowledgeToLanguageModel() {
        StubKnowledgeDao repository = new StubKnowledgeDao(List.of(
                new SearchHit("Spring AI", "Spring AI supports RAG.", 0.91),
                new SearchHit("Vector Store", "Vector stores provide similarity search.", 0.82)));
        RecordingLanguageModel languageModel = new RecordingLanguageModel();
        KnowledgeSearchService searchService =
                new KnowledgeSearchService(repository, new PassthroughReranker(), 30);
        RagChatService ragChatService = new RagChatService(searchService, languageModel);

        ChatAnswer answer = ragChatService.ask(7L, "Spring AI 如何实现知识库问答？");

        assertThat(languageModel.systemPrompt)
                .contains("[来源: Spring AI]", "Spring AI supports RAG.")
                .contains("[来源: Vector Store]", "Vector stores provide similarity search.");
        assertThat(languageModel.question).isEqualTo("Spring AI 如何实现知识库问答？");
        assertThat(repository.knowledgeBaseId).isEqualTo(7L);
        assertThat(repository.limit).isEqualTo(30);
        assertThat(answer.answer()).isEqualTo("基于知识库生成的回答");
        assertThat(answer.sources()).containsExactly("Spring AI", "Vector Store");
    }

    private static final class StubKnowledgeDao implements KnowledgeDao {

        private final List<SearchHit> hits;
        private Long knowledgeBaseId;
        private Integer limit;

        private StubKnowledgeDao(List<SearchHit> hits) {
            this.hits = hits;
        }

        @Override
        public boolean existsKnowledgeBase(Long knowledgeBaseId) {
            return true;
        }

        @Override
        public Long createKnowledgeBase(KnowledgeBase knowledgeBase) {
            return 1L;
        }

        @Override
        public Long save(KnowledgeDocument document, List<KnowledgeChunk> chunks) {
            return 1L;
        }

        @Override
        public List<SearchHit> search(Long knowledgeBaseId, String query, int limit) {
            this.knowledgeBaseId = knowledgeBaseId;
            this.limit = limit;
            return hits;
        }
    }

    private static final class RecordingLanguageModel implements LanguageModel {

        private String systemPrompt;
        private String question;

        @Override
        public String generate(String systemPrompt, String question) {
            this.systemPrompt = systemPrompt;
            this.question = question;
            return "基于知识库生成的回答";
        }
    }
}
