package com.example.spring_babel_rag.configuration;

/**
 * Stałe zawierające treści promptów dla agenta BlogWriterAgent.
 */
public final class BlogWriterPrompts {

    private BlogWriterPrompts() {}

    // --- writeBlogDraft ---

    public static final String DRAFT_MAIN = """
            Write a blog post about: %s
            
            Return only valid Markdown (no JSON, no code fences wrapping the whole answer).
            The first line must be a single H1 heading in the form: # <title>
            Text prepare in polish language.
            """;

    public static final String DRAFT_FALLBACK = """
            Write a blog post about: %s
            
            Return only valid Markdown (no JSON, no code fences wrapping the whole answer).
            The first line must be a single H1 heading in the form: # <title>
            Text prepare in polish language.
            %s
            """;

    // --- reviewAndImproveBlogDraft ---

    public static final String REVIEW_MAIN = """
            Review and improve the following blog post.
            Return only valid Markdown (no JSON, no code fences wrapping the whole answer).
            Keep the first line as a single H1 heading in the form: # <title>
            
            Content: %s
            """;

    public static final String REVIEW_FALLBACK = """
            Review and improve the following blog post.
            %s
            
            Tekst do recenzji:
            %s
            """;

    // --- LinkerBlogDraft ---

    public static final String LINKER_MAIN = """
            In the event of difficult technical terminology, replace the first instance of each term with a link to a reference site (owner documentation, or Wikipedia if nothing else found).
            Identify AT MOST 8 of the most important technical terms for linking.
            Do NOT add links inside code blocks or backtick spans.
            Return only valid Markdown (no JSON, no code fences wrapping the whole answer).
            Keep the first line as a single H1 heading in the form: # <title>
            
            Content: %s
            """;

    public static final String LINKER_FALLBACK = """
            Nie zmieniaj problematycznych linków. Zwróć prawidłowy markdown.
            %s
            
            Tekst do recenzji (bez bloków kodu):
            %s
            """;

    // --- editReviewedBlogPost ---

    public static final String EDIT_MAIN = """
            Correct and polish blog post and text diagrams.
            Return only valid Markdown (no JSON, no code fences wrapping the whole answer).
            Keep the first line as a single H1 heading in the form: # <title>
            
            Content: %s
            """;

    public static final String EDIT_FALLBACK = """
            Popraw i wypoleruj wpis bloga.
            %s
            
            Treść: %s
            """;

    // --- makeAttractiveReviewedBlogPost ---

    public static final String ATTRACTIVE_MAIN = """
            Make markdown file more attractive and easy to read.
            Return only valid Markdown (no JSON, no code fences wrapping the whole answer).
            Keep the first line as a single H1 heading in the form: # <title>
            
            Content: %s
            """;

    public static final String ATTRACTIVE_FALLBACK = """
            Correct markdown file
            %s
            
            Treść: %s
            """;
}

