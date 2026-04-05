package com.example.spring_babel_rag.error;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class FormatErrorHandlerTest {

    @Test
    void shouldDetectJsonParseError() {
        Exception jsonEx = new com.fasterxml.jackson.core.JsonParseException(null, "Unexpected character");
        assertTrue(FormatErrorHandler.isFormatError(jsonEx));
    }

    @Test
    void shouldNotDetectRegularRuntimeAsFormatError() {
        Exception ex = new RuntimeException("Connection reset by peer");
        assertFalse(FormatErrorHandler.isFormatError(ex));
    }

    @Test
    void shouldExtractMarkdownFromCodeFences() {
        String wrapped = "```markdown\n# Tytuł\n\nTreść wpisu.\n```";
        String result = FormatErrorHandler.extractCleanMarkdown(wrapped);
        assertTrue(result.contains("# Tytuł"));
        assertFalse(result.startsWith("```"));
    }

    @Test
    void shouldExtractContentFromJsonObject() {
        String json = "{\"content\": \"# Tytuł\\n\\nTreść wpisu.\"}";
        String result = FormatErrorHandler.extractCleanMarkdown(json);
        assertTrue(result.contains("# Tytuł"));
    }

    @Test
    void shouldPassThroughCleanMarkdown() {
        String clean = "# Tytuł\n\nAkapit treści.";
        String result = FormatErrorHandler.extractCleanMarkdown(clean);
        assertEquals(clean, result);
    }

    @Test
    void shouldValidateMarkdownWithH1() {
        String markdown = "# Tytuł\n\nTreść.";
        assertTrue(FormatErrorHandler.isValidMarkdownStructure(markdown));
    }

    @Test
    void shouldFailValidationWithoutH1() {
        String markdown = "Tytuł bez headinga\n\nTreść.";
        assertFalse(FormatErrorHandler.isValidMarkdownStructure(markdown));
    }

    @Test
    void shouldRepairMarkdownByAddingFallbackTitle() {
        String markdownWithoutH1 = "Treść wpisu bez tytułu.";
        String repaired = FormatErrorHandler.repairMarkdownWithFallbackTitle(markdownWithoutH1, "Mój Tytuł");
        assertTrue(repaired.startsWith("# Mój Tytuł"));
        assertTrue(repaired.contains("Treść wpisu bez tytułu."));
    }

    @Test
    void shouldNotRepairMarkdownThatAlreadyHasH1() {
        String markdown = "# Już jest Tytuł\n\nTreść wpisu.";
        String result = FormatErrorHandler.repairMarkdownWithFallbackTitle(markdown, "Inny Tytuł");
        assertEquals(markdown, result);
    }

    @Test
    void shouldReturnFallbackPromptString() {
        String prompt = FormatErrorHandler.getFallbackPrompt();
        assertNotNull(prompt);
        assertFalse(prompt.isBlank());
        assertTrue(prompt.contains("Markdown"));
    }
}

