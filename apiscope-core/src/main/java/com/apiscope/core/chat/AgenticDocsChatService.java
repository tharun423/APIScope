package com.apiscope.core.chat;

import com.apiscope.core.config.AgenticDocsProperties;
import com.apiscope.core.model.ChatRequest;
import com.apiscope.core.model.ChatResponse;
import com.apiscope.core.port.LlmPort;
import com.apiscope.core.port.VectorStorePort;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;

import java.util.List;

/**
 * RAG pipeline: sanitize question → retrieve context → call LLM.
 *
 * Override the system prompt via {@code apiscope.system-prompt} in application.properties.
 * Your custom prompt must contain the {@code {context}} placeholder.
 */
@Service
public class AgenticDocsChatService implements ChatPort {

    private static final Logger log = LoggerFactory.getLogger(AgenticDocsChatService.class);

    private static final String FALLBACK_ANSWER =
            "I could not find a relevant endpoint for that. Please check the API Explorer tab.";
    private static final String NO_LLM_MESSAGE =
            "AI chat is not configured. Please add an LLM provider (e.g. spring-ai-starter-model-ollama).";

    private final ObjectProvider<VectorStorePort> vectorStoreProvider;
    private final ObjectProvider<LlmPort> llmProvider;
    private final PromptBuilder promptBuilder;
    private final AgenticDocsProperties properties;

    public AgenticDocsChatService(ObjectProvider<VectorStorePort> vectorStoreProvider,
                                   ObjectProvider<LlmPort> llmProvider,
                                   AgenticDocsProperties properties) {
        this.vectorStoreProvider = vectorStoreProvider;
        this.llmProvider         = llmProvider;
        this.properties          = properties;
        this.promptBuilder       = new PromptBuilder(properties);
    }

    @Override
    public ChatResponse answer(ChatRequest request) {
        LlmPort llm = llmProvider.getIfAvailable();
        if (llm == null) return new ChatResponse(NO_LLM_MESSAGE);

        String question = QuestionSanitizer.sanitize(request.question());
        log.debug("[APIScope] Processing question: {}", question);

        String answer = llm.complete(promptBuilder.systemPrompt(), retrieveContext(question), question);
        return new ChatResponse(answer != null && !answer.isBlank() ? answer : FALLBACK_ANSWER);
    }

    @Override
    public Flux<String> streamAnswer(ChatRequest request) {
        LlmPort llm = llmProvider.getIfAvailable();
        if (llm == null) return Flux.just("AI chat is not configured. Please add an LLM provider.");

        String question = QuestionSanitizer.sanitize(request.question());
        log.debug("[APIScope] Streaming question: {}", question);

        return llm.stream(promptBuilder.systemPrompt(), retrieveContext(question), question);
    }

    private String retrieveContext(String question) {
        VectorStorePort store = vectorStoreProvider.getIfAvailable();
        if (store == null) return "";
        List<String> chunks = store.findRelevantContext(question, properties.topK());
        log.debug("[APIScope] Retrieved {} context chunks.", chunks.size());
        return String.join("\n---\n", chunks);
    }
}
