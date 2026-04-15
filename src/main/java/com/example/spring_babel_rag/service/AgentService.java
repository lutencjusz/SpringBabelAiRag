package com.example.spring_babel_rag.service;

import com.embabel.agent.api.common.Ai;
import com.embabel.agent.prompt.persona.RoleGoalBackstory;
import com.example.spring_babel_rag.model.AgentType;
import com.example.spring_babel_rag.error.FormatErrorHandler;
import com.example.spring_babel_rag.error.ResilientExecutor;
import org.springframework.stereotype.Service;

@Service
public class AgentService {

    private final ResilientExecutor resilientExecutor;

    public AgentService(ResilientExecutor resilientExecutor) {
        this.resilientExecutor = resilientExecutor;
    }

    public String sendPrompt(String prefix, String fallbackPrefix, String prompt, String role, RoleGoalBackstory person, String name, String description, AgentType agentType, Ai ai) throws Exception {
        if (agentType == AgentType.NATIVE) {
            return sendPromptToAgent(prompt, prefix, fallbackPrefix, role, person, name, description, ai);
        }
        return "";
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
                        .fromPrompt(prefix.formatted(prompt)),

                // Fallback funkcja (ze wzmocnionym prompt-em)
                () -> ai
                        .withLlmByRole(role)
                        .withPromptContributor(person)
                        .withId(name)
                        .creating(String.class)
                        .fromPrompt(fallbackPrefix.formatted(FormatErrorHandler.getFallbackPrompt(), prompt)),
                description);
    }
}