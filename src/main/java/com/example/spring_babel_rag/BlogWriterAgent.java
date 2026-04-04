package com.example.spring_babel_rag;

import com.embabel.agent.api.annotation.AchievesGoal;
import com.embabel.agent.api.annotation.Action;
import com.embabel.agent.api.annotation.Agent;
import com.embabel.agent.api.common.Ai;
import com.embabel.agent.domain.io.UserInput;
import com.example.spring_babel_rag.configuration.BlogWriteAgentProperties;
import com.example.spring_babel_rag.model.BlogDraft;
import com.example.spring_babel_rag.model.ReviewedPost;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

@Agent(description = "Agent piszący wpisy do bloga na dany temat")
public class BlogWriterAgent {

    private static final Log log = LogFactory.getLog(BlogWriterAgent.class);
    private final BlogWriteAgentProperties properties;

    public BlogWriterAgent(BlogWriteAgentProperties properties) {
        this.properties = properties;
    }

    @Action(description = "Translate question for English")
    public String translateQuestion(UserInput userInput, Ai ai) {
        return ai
                .withDefaultLlm()
                .withId("tłumacz-pytania-na-angielski")
                .creating(String.class)
                .fromPrompt("""
                        You are a translator. if userInput is in Polish, translate this question into English.
                        
                        Question: %s
                        """.formatted(userInput.getContent()));
    }

    @Action(description = "Write a blog post draft based on the user input topic")
    public BlogDraft writeBlogDraft(String translateUserInput, Ai ai) {
        return ai
                .withDefaultLlm()
                .withId("szkicownik-wpisów-bloga")
                .creating(BlogDraft.class)
                .fromPrompt("""
                        You are a software developer and educator writing a blog post.
                        Write a blog post about: %s
                        
                        Keep it practical and beginner friendly.
                        Use short sentences and plain language.
                        Include code examples but keep them short and simple.
                        Write the content in Markdown.
                        """.formatted(translateUserInput));
    }

    @Action(description = "Check and correct the blog post. Fix any technical errors and tighten the writing.")
    public ReviewedPost reviewAndImproveBlogDraft(BlogDraft draft, Ai ai) {
        return ai
                .withDefaultLlm()
                .withId("recenzent-szkiców-bloga")
                .creating(ReviewedPost.class)
                .fromPrompt("""
                        You are a technical editor. Review and improve this blog post.
                        
                        Title: %s
                        Content: %s
                        
                        Fix any technical errors. Tighten the writing.
                        Provide the revised title, revised content, and a brief
                        summary of the changes you made as feedback.
                        """.formatted(draft.title(), draft.content()));
    }

    @AchievesGoal(description = "all the blog post is translated on polish language")
    @Action(description = "Translate the blog post on polish language")
    public ReviewedPost translateOnPolishReviewedBlogPost(ReviewedPost reviewedPost, Ai ai) {
        ReviewedPost TranslatedBlogPost = ai
                .withDefaultLlm()
                .withId("tłumacz-szkiców-bloga-na-polski")
                .creating(ReviewedPost.class)
                .fromPrompt("""
                        You are a translator. Translate this blog post into Polish.
                        
                        Title: %s
                        Content: %s
                        """.formatted(reviewedPost.title(), reviewedPost.content()));
        writeToFile(TranslatedBlogPost);
        return TranslatedBlogPost;
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
