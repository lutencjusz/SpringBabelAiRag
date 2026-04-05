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
                    .withDefaultLlm()
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
                                    In the event of difficult technical terminology, replace the first instance of each term with a verified link to a reference site (owner documentation and if you find nothing add Wikipedia) providing an explanation of the term.

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

    @AchievesGoal(description = "blog post is edited and corrected")
    @Action(description = "Correct and edit polish blog post, making sure it is linguistically accurate and polished for publication.")
    public ReviewedPost editReviewedBlogPost(ReviewedPost reviewedPost, Ai ai) {
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
                                    """.formatted(reviewedPost.title(), reviewedPost.content())),

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
                                    """.formatted(FormatErrorHandler.getFallbackPrompt(), reviewedPost.title(), reviewedPost.content())),

                    "Edycja i poprawa redakcyjna wpisu");

            // Wyczyść format, jeśli potrzeba
            editedMarkdown = FormatErrorHandler.extractCleanMarkdown(editedMarkdown);

            String title = extractTitleFromMarkdown(editedMarkdown, reviewedPost.title());
            ReviewedPost translatedBlogPost = new ReviewedPost(title, editedMarkdown, reviewedPost.feedback());
            writeToFile(translatedBlogPost);
            return translatedBlogPost;

        } catch (Exception e) {
            log.error("Błąd przy edycji wpisu: " + e.getMessage(), e);
            throw new RuntimeException(e);
        }
    }

    /**
     * Znajduje tytuł w pierwszej linii markdowna, który powinien być w formacie H1. Jeśli nie znajdzie, używa fallbacku (np. pytania) jako tytułu.
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

    private void writeToFile(ReviewedPost post) {
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
