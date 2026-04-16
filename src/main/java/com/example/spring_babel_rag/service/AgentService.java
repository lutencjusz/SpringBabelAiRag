package com.example.spring_babel_rag.service;

import com.embabel.agent.api.common.Ai;
import com.embabel.agent.prompt.persona.RoleGoalBackstory;
import com.example.spring_babel_rag.configuration.CopilotProperties;
import com.example.spring_babel_rag.error.FormatErrorHandler;
import com.example.spring_babel_rag.error.ResilientExecutor;
import com.example.spring_babel_rag.model.AgentType;
import com.github.copilot.sdk.CopilotClient;
import com.github.copilot.sdk.events.SessionUsageInfoEvent;
import com.github.copilot.sdk.json.MessageOptions;
import com.github.copilot.sdk.json.PermissionHandler;
import com.github.copilot.sdk.json.SessionConfig;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

@Service
public class AgentService {

    private final ResilientExecutor resilientExecutor;
    private final CopilotProperties copilotProperties;

    public AgentService(ResilientExecutor resilientExecutor, CopilotProperties copilotProperties) {
        this.resilientExecutor = resilientExecutor;
        this.copilotProperties = copilotProperties;
    }

    public String sendPrompt(String prefix, String fallbackPrefix, String prompt, String role, RoleGoalBackstory person, String name, String description, AgentType agentType, Ai ai) throws Exception {
        if (agentType == AgentType.NATIVE) {
            return sendPromptToAgent(prefix, fallbackPrefix, prompt, role, person, name, description, ai);
        } else if (agentType == AgentType.COPILOT) {
            return sendPromptToCopilotSdkAgent(prefix, fallbackPrefix, prompt, role, person, name, description, ai);
        }
        return "";
    }

    static String formatMainPrompt(String promptTemplate, String promptContent) {
        return substitutePlaceholders(promptTemplate, promptContent);
    }

    static String formatFallbackPrompt(String fallbackPromptTemplate, String promptContent) {
        return substitutePlaceholders(fallbackPromptTemplate, FormatErrorHandler.getFallbackPrompt(), promptContent);
    }

    static String substitutePlaceholders(String template, String... values) {
        String result = template;
        for (String value : values) {
            int placeholderIndex = result.indexOf("%s");
            if (placeholderIndex < 0) {
                throw new IllegalArgumentException("Brak placeholdera %s w szablonie promptu");
            }
            String safeValue = value == null ? "" : value;
            result = result.substring(0, placeholderIndex) + safeValue + result.substring(placeholderIndex + 2);
        }
        return result;
    }

    static <T> T awaitWithTimeout(CompletableFuture<T> future, Duration timeout) throws Exception {
        return future.get(timeout.toMillis(), TimeUnit.MILLISECONDS);
    }

    private String sendPromptToAgent(String prefix, String fallbackPrefix, String prompt, String role, RoleGoalBackstory person, String name, String description, Ai ai) throws Exception {
        // Prompt główny - operuje TYLKO na tekście bez bloków kodu
        return resilientExecutor.executeWithRetryAndFormatFallback(
                // Główna funkcja
                () -> ai
                        .withLlmByRole(role)
                        .withPromptContributor(person)
                        .withId(name)
                        .creating(String.class)
                        .fromPrompt(formatMainPrompt(prefix, prompt)),

                // Fallback funkcja (ze wzmocnionym prompt-em)
                () -> ai
                        .withLlmByRole(role)
                        .withPromptContributor(person)
                        .withId(name)
                        .creating(String.class)
                        .fromPrompt(formatFallbackPrompt(fallbackPrefix, prompt)),
                description);
    }

    private String sendPromptToCopilotSdkAgent(String prefix, String fallbackPrefix, String prompt, String role, RoleGoalBackstory person, String name, String description, Ai ai) throws Exception {
        try (var client = new CopilotClient()) {
            client.start().get();
            var session = client.createSession(
                            new SessionConfig()
                                    .setOnPermissionRequest(PermissionHandler.APPROVE_ALL)
                                    .setModel("claude-sonnet-4.5"))
                    .get();
            session.on(SessionUsageInfoEvent.class, usage -> {
                var data = usage.getData();
                System.out.println("\n--- Informacje o sesji ---");
                System.out.println("Agent:" + name);
                System.out.println("Użyty model: claude-sonnet-4.5");
                System.out.println("\n--- Metryki użycia ---");
                System.out.println("Użytych tokenów: " + (int) data.currentTokens());
                System.out.println("Limit tokenów: " + (int) data.tokenLimit());
                System.out.println("Ilość komunikatów: " + (int) data.messagesLength());
                System.out.println("Długość komunikatów: " + (int) data.messagesLength());
                System.out.println("-----------------------------");
            });
            var completable = session.sendAndWait(new MessageOptions().setPrompt(formatMainPrompt(prefix, prompt) + " Od razu przystępuj do realizacji planu. Nie potrzebuję żadnych dodatkowych informacji. Odpowiadaj tylko i wyłącznie gotowym rozwiązaniem, bez żadnych wstępów i analiz."));
            var response = awaitWithTimeout(completable, copilotProperties.timeout());
            System.out.println("Id interakcji: " + response.getData().interactionId());
            System.out.println("Id komunikatu: " + response.getData().messageId());
            System.out.println("Użyte narzędzia: " + response.getData().toolRequests().toString());
            return response.getData().content();
        }
    }
}