package com.apiscope.core.infrastructure;

import com.apiscope.core.port.LlmPort;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;

@Component
@ConditionalOnBean(ChatClient.Builder.class)
public class LlmAdapter implements LlmPort {

    private final ChatClient chatClient;

    public LlmAdapter(ChatClient.Builder chatClientBuilder) {
        this.chatClient = chatClientBuilder.build();
    }

    @Override
    public String complete(String systemPromptTemplate, String context, String question) {
        String result = chatClient.prompt()
                .system(s -> s.text(systemPromptTemplate).param("context", context))
                .user(question)
                .call()
                .content();
        return (result != null) ? result : "";
    }

    @Override
    public Flux<String> stream(String systemPromptTemplate, String context, String question) {
        return chatClient.prompt()
                .system(s -> s.text(systemPromptTemplate).param("context", context))
                .user(question)
                .stream()
                .content()
                .filter(token -> token != null && !token.isEmpty());
    }
}
