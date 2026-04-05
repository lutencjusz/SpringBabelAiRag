package com.example.spring_babel_rag;

import com.embabel.agent.api.annotation.AchievesGoal;
import com.embabel.agent.api.annotation.Action;
import com.embabel.agent.api.annotation.Agent;
import com.embabel.agent.api.common.Ai;
import com.embabel.agent.domain.io.UserInput;
import com.example.spring_babel_rag.configuration.BlogWriteAgentProperties;
import com.example.spring_babel_rag.configuration.Persons;
import com.example.spring_babel_rag.model.BlogDraft;
import com.example.spring_babel_rag.model.ReviewedPost;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Agent(description = "Agent piszący wpisy do bloga na dany temat")
public class BlogWriterAgent {

    private static final Log log = LogFactory.getLog(BlogWriterAgent.class);
    private static final Pattern FIRST_H1_PATTERN = Pattern.compile("(?m)^#\\s+(.+?)\\s*$");
    private final BlogWriteAgentProperties properties;

    public BlogWriterAgent(BlogWriteAgentProperties properties) {
        this.properties = properties;
    }

    @Action(description = "Translate question for English")
    public String translateQuestion(UserInput userInput, Ai ai) {
        return ai
                .withLlmByRole("translator")
                .withId("tłumacz-pytania-na-angielski")
                .withPromptContributor(Persons.TRANSLATOR).creating(String.class)
                .fromPrompt("""
                        Question: %s
                        """.formatted(userInput.getContent()));
    }

    @Action(description = "Write a blog post draft based on the user input topic")
    public BlogDraft writeBlogDraft(String translateUserInput, Ai ai) {
        String markdown = ai
                .withDefaultLlm()
                .withId("szkicownik-wpisów-bloga")
                .withPromptContributor(Persons.DEVELOPER)
                .creating(String.class)
                .fromPrompt("""
                        Write a blog post about: %s

                        Return only valid Markdown (no JSON, no code fences wrapping the whole answer).
                        The first line must be a single H1 heading in the form: # <title>
                        """.formatted(translateUserInput));

        String title = extractTitleFromMarkdown(markdown, translateUserInput);
        return new BlogDraft(title, markdown);
    }

    @Action(description = "Check and correct the blog post. Fix any technical errors and tighten the writing.")
    public ReviewedPost reviewAndImproveBlogDraft(BlogDraft draft, Ai ai) {
        String reviewedMarkdown = ai
                .withLlmByRole("reviewer")
                .withPromptContributor(Persons.REVIEWER)
                .withId("recenzent-szkiców-bloga")
                .creating(String.class)
                .fromPrompt("""
                        Review and improve the following blog post.
                        Return only valid Markdown (no JSON, no code fences wrapping the whole answer).
                        Keep the first line as a single H1 heading in the form: # <title>
                        In the event of difficult technical terminology, replace the first instance of each term with a link to a reference site (e.g., Wikipedia) providing an explanation of the term.

                        Title: %s
                        Content: %s
                        """.formatted(draft.title(), draft.content()));

        String title = extractTitleFromMarkdown(reviewedMarkdown, draft.title());
        String feedback = buildShortFeedback(draft.content(), reviewedMarkdown);
        return new ReviewedPost(title, reviewedMarkdown, feedback);
    }

    @AchievesGoal(description = "blog post is translated on polish language")
    @Action(description = "Translate the blog post on polish language")
    public ReviewedPost translateOnPolishReviewedBlogPost(ReviewedPost reviewedPost, Ai ai) {
        String translatedMarkdown = ai
                .withLlmByRole("translator")
                .withPromptContributors(List.of(Persons.TRANSLATOR, Persons.EDITOR_PL))
                .withId("tłumacz-szkiców-bloga-na-polski")
                .creating(String.class)
                .fromPrompt("""
                        Translate the following blog post to Polish.
                        Return only valid Markdown (no JSON, no code fences wrapping the whole answer).
                        Keep the first line as a single H1 heading in the form: # <title>

                        Title: %s
                        Content: %s
                        """.formatted(reviewedPost.title(), reviewedPost.content()));

        String title = extractTitleFromMarkdown(translatedMarkdown, reviewedPost.title());
        ReviewedPost translatedBlogPost = new ReviewedPost(title, translatedMarkdown, reviewedPost.feedback());
        writeToFile(translatedBlogPost);
        return translatedBlogPost;
    }

    private String extractTitleFromMarkdown(String markdown, String fallback) {
        Matcher matcher = FIRST_H1_PATTERN.matcher(markdown);
        if (matcher.find()) {
            return matcher.group(1).trim();
        }
        return fallback.replaceFirst("(?i)^question:\\s*", "").trim();
    }

    private String buildShortFeedback(String originalMarkdown, String reviewedMarkdown) {
        boolean hasH1 = FIRST_H1_PATTERN.matcher(reviewedMarkdown).find();
        int delta = reviewedMarkdown.length() - originalMarkdown.length();

        if (!hasH1) {
            return "Korekta wykonana; dodaj naglowek H1 na poczatku.";
        }
        if (delta < -80) {
            return "Korekta wykonana; tresc zostala skrocona i uproszczona.";
        }
        if (delta > 80) {
            return "Korekta wykonana; dodano doprecyzowania techniczne.";
        }
        return "Korekta wykonana; jezyk i technikalia poprawione.";
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
