package com.example.knowledge.application;

import com.example.knowledge.domain.ChatAnswer;
import com.example.knowledge.domain.SearchHit;
import com.example.knowledge.port.KnowledgeRepository;
import com.example.knowledge.port.LanguageModel;
import java.util.List;
import java.util.stream.Collectors;

public class RagChatService {

    private static final int DEFAULT_SEARCH_LIMIT = 4;
    private static final String EMPTY_CONTEXT = "未检索到相关知识。";
    private static final String SYSTEM_PROMPT_TEMPLATE = """
            你是一个知识库问答助手。请只根据下面的知识库内容回答问题。
            如果知识库内容不足以回答，请明确说明不知道，不要编造。

            知识库内容：
            %s
            """;

    private final KnowledgeRepository knowledgeRepository;
    private final LanguageModel languageModel;

    public RagChatService(KnowledgeRepository knowledgeRepository, LanguageModel languageModel) {
        this.knowledgeRepository = knowledgeRepository;
        this.languageModel = languageModel;
    }

    /**
     * 检索知识并生成回答。
     */
    public ChatAnswer ask(Long knowledgeBaseId, String question) {
        List<SearchHit> hits = knowledgeRepository.search(knowledgeBaseId, question, DEFAULT_SEARCH_LIMIT);
        String context = buildContext(hits);
        String answer = languageModel.generate(SYSTEM_PROMPT_TEMPLATE.formatted(context), question);
        List<String> sources = hits.stream()
                .map(SearchHit::title)
                .distinct()
                .toList();
        return new ChatAnswer(answer, sources);
    }

    private String buildContext(List<SearchHit> hits) {
        if (hits.isEmpty()) {
            return EMPTY_CONTEXT;
        }
        return hits.stream()
                .map(hit -> "[来源: %s]%n%s".formatted(hit.title(), hit.content()))
                .collect(Collectors.joining("\n\n"));
    }
}
