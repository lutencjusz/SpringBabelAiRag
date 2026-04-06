package com.example.spring_babel_rag;

import com.embabel.agent.api.annotation.AchievesGoal;
import com.embabel.agent.api.annotation.Action;
import com.embabel.agent.api.annotation.Agent;
import com.embabel.agent.api.common.Ai;
import com.embabel.agent.domain.io.UserInput;
import com.example.spring_babel_rag.configuration.BlogWriteAgentProperties;
import com.example.spring_babel_rag.configuration.Persons;
import com.example.spring_babel_rag.error.FormatErrorHandler;
import com.example.spring_babel_rag.error.ResilientExecutor;
import com.example.spring_babel_rag.model.EditedPost;
import com.example.spring_babel_rag.model.LinkedPost;
import com.example.spring_babel_rag.model.MarkdownPost;
import com.example.spring_babel_rag.model.ReviewedPost;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Agent(description = "Agent piszący wpisy do bloga na dany temat")
@Component
public class BlogWriterAgent {

    private static final Log log = LogFactory.getLog(BlogWriterAgent.class);
    private static final Pattern FIRST_H1_PATTERN = Pattern.compile("(?m)^#\\s+(.+?)\\s*$");
    private final BlogWriteAgentProperties properties;
    private final ResilientExecutor resilientExecutor;

    public BlogWriterAgent(BlogWriteAgentProperties properties, ResilientExecutor resilientExecutor) {
        this.properties = properties;
        this.resilientExecutor = resilientExecutor;
    }

    @Action(description = "Write a blog post draft based on the user input topic")
    public String writeBlogDraft(UserInput userInput, Ai ai) {
        try {
            return resilientExecutor.executeWithRetry(() -> ai
                            .withLlmByRole("writer")
                            .withId("szkicownik-wpisów-bloga")
                            .withPromptContributor(Persons.DEVELOPER)
                            .creating(String.class)
                            .fromPrompt("""
                                    Write a blog post about: %s
                                    
                                    Return only valid Markdown (no JSON, no code fences wrapping the whole answer).
                                    The first line must be a single H1 heading in the form: # <title>
                                    Text prepare in polish language.
                                    """.formatted(userInput.getContent())),
                    "Generowanie szkicu wpisu bloga");
        } catch (Exception e) {
            log.error("Błąd przy generowaniu szkicu wpisu: " + e.getMessage(), e);
            throw new RuntimeException(e);
        }
    }

    @Action(description = "Check and correct the blog post. Fix any technical errors and tighten the writing.")
    public ReviewedPost reviewAndImproveBlogDraft(String draft, Ai ai) {
        try {
            // Prompt główny
            String reviewedMarkdown = resilientExecutor.executeWithRetryAndFormatFallback(
                    // Główna funkcja
                    () -> ai
                            .withLlmByRole("reviewer")
                            .withPromptContributor(Persons.REVIEWER)
                            .withId("recenzent-szkiców-bloga")
                            .creating(String.class)
                            .fromPrompt("""
                                    Review and improve the following blog post.
                                    Return only valid Markdown (no JSON, no code fences wrapping the whole answer).
                                    Keep the first line as a single H1 heading in the form: # <title>
                                    
                                    Content: %s
                                    """.formatted(draft)),

                    // Fallback funkcja (ze wzmocnionym prompt-em)
                    () -> ai
                            .withLlmByRole("reviewer")
                            .withPromptContributor(Persons.REVIEWER)
                            .withId("recenzent-szkiców-bloga-fallback")
                            .creating(String.class)
                            .fromPrompt("""
                                    Review and improve the following blog post.
                                    %s
                                    
                                    Tekst do recenzji:
                                    %s
                                    """.formatted(FormatErrorHandler.getFallbackPrompt(), draft)),

                    "Recenzja i poprawa szkicu wpisu");

            // Wyczyść format, jeśli potrzeba
            reviewedMarkdown = FormatErrorHandler.extractCleanMarkdown(reviewedMarkdown);

            String title = extractTitleFromMarkdown(reviewedMarkdown, "");
            String feedback = buildShortFeedback(draft, reviewedMarkdown);
            log.debug("reviewAndImproveBlogDraft: title: " + title + ", feedback:" + feedback);
            return new ReviewedPost(title, reviewedMarkdown, feedback);

        } catch (Exception e) {
            log.error("Błąd przy recenzji wpisu: " + e.getMessage(), e);
            throw new RuntimeException(e);
        }
    }

    @Action(description = "Link and verify linked difficult technical terminology")
    public LinkedPost LinkerBlogDraft(ReviewedPost reviewedPost, Ai ai) {
        try {
            // Wyciągnij bloki kodu - nie wymagają linkowania terminów i są bardzo kosztowne tokenowo
            List<String> extractedCodeBlocks = new java.util.ArrayList<>();
            String proseContent = extractCodeBlocks(reviewedPost.content(), extractedCodeBlocks);

            // Prompt główny - operuje TYLKO na tekście bez bloków kodu
            String linkedProse = resilientExecutor.executeWithRetryAndFormatFallback(
                    // Główna funkcja
                    () -> ai
                            .withLlmByRole("linker")
                            .withPromptContributor(Persons.REVIEWER)
                            .withId("linker-szkiców-bloga")
                            .creating(String.class)
                            .fromPrompt("""
                                    In the event of difficult technical terminology, replace the first instance of each term with a link to a reference site (owner documentation, or Wikipedia if nothing else found).
                                    Identify AT MOST 8 of the most important technical terms for linking.
                                    Do NOT add links inside code blocks or backtick spans.
                                    Return only valid Markdown (no JSON, no code fences wrapping the whole answer).
                                    Keep the first line as a single H1 heading in the form: # <title>
                                    
                                    Content: %s
                                    """.formatted(proseContent)),

                    // Fallback funkcja (ze wzmocnionym prompt-em)
                    () -> ai
                            .withLlmByRole("reviewer")
                            .withPromptContributor(Persons.REVIEWER)
                            .withId("linker-szkiców-bloga-fallback")
                            .creating(String.class)
                            .fromPrompt("""
                                    Nie zmieniaj problematycznych linków. Zwróć prawidłowy markdown.
                                    %s
                                    
                                    Tekst do recenzji (bez bloków kodu):
                                    %s
                                    """.formatted(FormatErrorHandler.getFallbackPrompt(), proseContent)),

                    "Dodanie linków i ich weryfikacja");

            // Przywróć bloki kodu do wynikowego markdown
            String linkedBlogDraft = restoreCodeBlocks(linkedProse, extractedCodeBlocks);

            String feedback = buildShortFeedback(reviewedPost.content(), linkedBlogDraft);
            return new LinkedPost(reviewedPost.title(), linkedBlogDraft, feedback);

        } catch (Exception e) {
            log.error("Błąd przy recenzji wpisu: " + e.getMessage(), e);
            throw new RuntimeException(e);
        }
    }

    /**
     * Wyciąga bloki kodu z Markdown i zastępuje je placeholderami {{CODE_BLOCK_N}}.
     * Zmniejsza ilość tokenów wysyłanych do LLM (bloki kodu nie wymagają linkowania terminów).
     *
     * @param markdown     pełny tekst Markdown
     * @param codeBlocks   lista do wypełnienia wyciągniętymi blokami kodu
     * @return tekst Markdown z placeholderami zamiast bloków kodu
     */
    private String extractCodeBlocks(String markdown, List<String> codeBlocks) {
        Pattern codeBlockPattern = Pattern.compile("(?s)```[^\\n]*\\n.*?```");
        java.util.regex.Matcher matcher = codeBlockPattern.matcher(markdown);
        StringBuilder result = new StringBuilder();
        int lastEnd = 0;
        int blockIndex = 0;
        while (matcher.find()) {
            result.append(markdown, lastEnd, matcher.start());
            result.append("{{CODE_BLOCK_").append(blockIndex).append("}}");
            codeBlocks.add(matcher.group());
            blockIndex++;
            lastEnd = matcher.end();
        }
        result.append(markdown.substring(lastEnd));
        return result.toString();
    }

    /**
     * Przywraca bloki kodu do tekstu Markdown w miejsce placeholderów {{CODE_BLOCK_N}}.
     *
     * @param content    tekst Markdown z placeholderami
     * @param codeBlocks lista oryginalnych bloków kodu do przywrócenia
     * @return tekst Markdown z przywróconymi blokami kodu
     */
    private String restoreCodeBlocks(String content, List<String> codeBlocks) {
        for (int i = 0; i < codeBlocks.size(); i++) {
            content = content.replace("{{CODE_BLOCK_" + i + "}}", codeBlocks.get(i));
        }
        return content;
    }

    @Action(description = "Correct and edit polish blog post, making sure it is linguistically accurate and polished for publication.")
    public EditedPost editReviewedBlogPost(LinkedPost linkedPost, Ai ai) {
        try {
            // Prompt główny
            String editedMarkdown = resilientExecutor.executeWithRetryAndFormatFallback(
                    // Główna funkcja
                    () -> ai
                            .withLlmByRole("editor")
                            .withPromptContributors(List.of(Persons.EDITOR_PL))
                            .withId("redaktor-szkiców-bloga")
                            .creating(String.class)
                            .fromPrompt("""
                                    Correct and polish blog post and text diagrams.
                                    Return only valid Markdown (no JSON, no code fences wrapping the whole answer).
                                    Keep the first line as a single H1 heading in the form: # <title>
                                    
                                    Title: %s
                                    Content: %s
                                    """.formatted(linkedPost.title(), linkedPost.content())),

                    // Fallback funkcja (ze wzmocnionym promptem)
                    () -> ai
                            .withLlmByRole("editor")
                            .withPromptContributors(List.of(Persons.EDITOR_PL))
                            .withId("redaktor-szkiców-bloga-fallback")
                            .creating(String.class)
                            .fromPrompt("""
                                    Popraw i wypoleruj wpis bloga.
                                    %s
                                    
                                    Tytuł: %s
                                    Treść: %s
                                    """.formatted(FormatErrorHandler.getFallbackPrompt(), linkedPost.title(), linkedPost.content())),

                    "Edycja i poprawa redakcyjna wpisu");

            // Wyczyść format, jeśli potrzeba
            editedMarkdown = FormatErrorHandler.extractCleanMarkdown(editedMarkdown);

            String feedback = buildShortFeedback(linkedPost.content(), editedMarkdown);
            String title = extractTitleFromMarkdown(editedMarkdown, linkedPost.title());
            return new EditedPost(title, editedMarkdown, feedback);

        } catch (Exception e) {
            log.error("Błąd przy edycji wpisu: " + e.getMessage(), e);
            throw new RuntimeException(e);
        }
    }

    @AchievesGoal(description = "blog post is attractive Markdown file and is ready for publication")
    @Action(description = "Make your Markdown output file more attractive by adding embellishments, design structures, and more engaging formatting. Use Markdown features like blockquotes, lists, bold/italic text, emojis, and more to enhance readability and visual appeal. Ensure the content remains clear and informative while making it more enjoyable to read.")
    public MarkdownPost makeAttractiveReviewedBlogPost(EditedPost reviewedPost, Ai ai) {
        try {
            // Prompt główny
            String markdown = resilientExecutor.executeWithRetryAndFormatFallback(
                    // Główna funkcja
                    () -> ai
                            .withLlmByRole("md_expert")
                            .withPromptContributors(List.of(Persons.MARKDOWN_EXPERT))
                            .withId("ekspert-markdown")
                            .creating(String.class)
                            .fromPrompt("""
                                    Make markdown file more attractive and easy to read.
                                    Return only valid Markdown (no JSON, no code fences wrapping the whole answer).
                                    Keep the first line as a single H1 heading in the form: # <title>
                                    
                                    Title: %s
                                    Content: %s
                                    """.formatted(reviewedPost.title(), reviewedPost.content())),

                    // Fallback funkcja (ze wzmocnionym promptem)
                    () -> ai
                            .withLlmByRole("md_expert")
                            .withPromptContributors(List.of(Persons.MARKDOWN_EXPERT))
                            .withId("ekspert-markdown")
                            .creating(String.class)
                            .fromPrompt("""
                                    Correct markdown file
                                    %s
                                    
                                    Tytuł: %s
                                    Treść: %s
                                    """.formatted(FormatErrorHandler.getFallbackPrompt(), reviewedPost.title(), reviewedPost.content())),

                    "Edycja i poprawa pliku Markdown");

            MarkdownPost translatedBlogPost = new MarkdownPost(reviewedPost.title(), markdown, reviewedPost.feedback());
            writeToFile(translatedBlogPost);
            return translatedBlogPost;
        } catch (Exception e) {
            log.error("Błąd przy edycji wpisu: " + e.getMessage(), e);
            throw new RuntimeException(e);
        }
    }

    /**
     * Znajduje tytuł w pierwszej linii markdowna, który powinien być w formacie H1. Jeśli nie znajdzie, używa fallbacku (np. pytania) jako tytułu.
     *
     * @param markdown - tekst zawierający cały szkic wpisu, z którego należy wyciągnąć tytuł. Tytuł powinien być zawarty w pierwszej linii
     * @param fallback - uwagi agenta do tytułu
     * @return - tytuł wpisu, który będzie użyty do nazwania pliku. Powinien być krótki i zwięzły, najlepiej do 5 słów.
     */
    private String extractTitleFromMarkdown(String markdown, String fallback) {
        Matcher matcher = FIRST_H1_PATTERN.matcher(markdown);
        if (matcher.find()) {
            return matcher.group(1).trim();
        }
        // Fallback: jeśli nie ma H1, użyj fallback albo spróbuj naprawić strukturę
        String fallbackTitle = fallback.replaceFirst("(?i)^question:\\s*", "").trim();
        if (fallbackTitle.isEmpty()) {
            fallbackTitle = "Untitled Blog Post";
        }
        log.warn("Nie znaleziono H1 headinga w Markdownie, użyto fallback tytułu: " + fallbackTitle);
        return fallbackTitle;
    }

    private String buildShortFeedback(String originalMarkdown, String reviewedMarkdown) {
        boolean hasH1 = FIRST_H1_PATTERN.matcher(reviewedMarkdown).find();
        int delta = reviewedMarkdown.length() - originalMarkdown.length();

        if (!hasH1) {
            return "Korekta wykonana; dodaj nagłówek H1 na początku.";
        }
        if (delta < -80) {
            return "Korekta wykonana; treść została skrócona i uproszczona.";
        }
        if (delta > 80) {
            return "Korekta wykonana; dodano doprecyzowania techniczne.";
        }
        return "Korekta wykonana; język i technikalia poprawione.";
    }

    private void writeToFile(MarkdownPost post) {
        String filename = post.title()
                .toLowerCase()
                .replaceAll("[^a-z0-9]+", "-")
                .replaceAll("^-|-$", "")
                + ".md";

        Path outputDir = Path.of(properties.outputDir());
        Path filePath = outputDir.resolve(filename);

        try {
            Files.createDirectories(outputDir);
            Files.writeString(filePath, post.content());
            log.info("Post zapisany do: " + filePath.toAbsolutePath());
        } catch (IOException e) {
            log.error(String.format("Błąd przy probie zapisu bloga do pliku: %s: %s%n", filePath, e.getMessage()));
        }
    }
}
