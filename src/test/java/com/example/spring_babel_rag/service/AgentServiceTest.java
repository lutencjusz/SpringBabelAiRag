package com.example.spring_babel_rag.service;

import com.example.spring_babel_rag.configuration.BlogWriterPrompts;
import com.example.spring_babel_rag.error.FormatErrorHandler;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.concurrent.CompletableFuture;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AgentServiceTest {

    @Test
    void shouldFormatReviewPromptUsingTemplateNotUserContent() {
        String draft = "# Tytuł\n\nSkuteczność wynosi 100%\\Windows i nie może wywołać %\\.";

        String formatted = AgentService.formatMainPrompt(BlogWriterPrompts.REVIEW_MAIN, draft);

        assertTrue(formatted.contains("Content: " + draft));
        assertTrue(formatted.startsWith("Review and improve the following blog post."));
    }

    @Test
    void shouldFormatFallbackPromptWithInstructionAndUserContent() {
        String draft = "# Tytuł\n\nŚcieżka C:\\tools\\mcp i wartość 100%.";

        String formatted = AgentService.formatFallbackPrompt(BlogWriterPrompts.REVIEW_FALLBACK, draft);

        assertTrue(formatted.contains(FormatErrorHandler.getFallbackPrompt()));
        assertTrue(formatted.contains(draft));
        assertEquals(1, formatted.split("Tekst do recenzji:", -1).length - 1);
    }

    @Test
    void shouldAwaitCopilotResponseWithConfiguredTimeout() throws Exception {
        CompletableFuture<String> future = CompletableFuture.completedFuture("ok");

        String response = AgentService.awaitWithTimeout(future, Duration.ofSeconds(240));

        assertEquals("ok", response);
        assertTrue(future.isDone());
    }
}

