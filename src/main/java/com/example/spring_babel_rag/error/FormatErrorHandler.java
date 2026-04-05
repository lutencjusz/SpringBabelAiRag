package com.example.spring_babel_rag.error;

/**
 * Handler dla błędów formatu ze wbudowanym fallback-iem.
 * Próbuje naprawić błędy JSON/parsowania za pomocą fallback prompt-a lub fallback parsera.
 */
public class FormatErrorHandler {

    private static final String FALLBACK_PROMPT = """
            Odpowiedź powinna być **czystym Markdownem** bez żadnych opakowań JSON, kodów, ani znaków formatujących.
            
            WYMAGANE:
            - Pierwsza linia MUSI być nagłówkiem H1: # <tytuł>
            - Tylko zwykły Markdown (headingi, listy, linki, tekst)
            - Bez JSON-a
            - Bez markdown code fences (```...```)
            - Bez dodatkowych znaków ani metadanych
            """;

    /**
     * sprawdza, czy błąd jest błędem formatu (JSON, parsing).
     *
     * @param throwable wyjątek do sprawdzenia
     * @return true, jeśli błąd jest błędem formatu
     */
    public static boolean isFormatError(Throwable throwable) {
        if (throwable == null) {
            return false;
        }

        String message = throwable.getMessage() != null ? throwable.getMessage().toLowerCase() : "";
        String className = throwable.getClass().getSimpleName();

        return className.contains("JsonParseException") ||
               className.contains("JsonMappingException") ||
               className.contains("ParseException") ||
               className.contains("MismatchedInputException") ||
               message.contains("unexpected character") ||
               message.contains("no viable alternative") ||
               message.contains("malformed json") ||
               message.contains("syntax error") ||
               message.contains("invalid json");
    }

    /**
     * Ekstrakcji surowy Markdown z zaśmieconego output (usuwa JSON wrapper, backtick's itp.).
     *
     * @param content zanieczyszczony tekst
     * @return czysty Markdown lub oryginalny tekst. jeśli nie da się wyciągnąć
     */
    public static String extractCleanMarkdown(String content) {
        if (content == null || content.isBlank()) {
            return "";
        }

        String cleaned = content.trim();

        // Usuń markdown code fences: ```lang\n ... \n``` -> wyciągnij środek
        java.util.regex.Matcher fenceMatcher = java.util.regex.Pattern
                .compile("(?s)^```\\w*\\r?\\n(.+?)\\r?\\n```\\s*$")
                .matcher(cleaned);
        if (fenceMatcher.matches()) {
            cleaned = fenceMatcher.group(1).trim();
        } else {
            // Fallback: usuń tylko linię otwierającą i zamykającą jeśli są
            cleaned = cleaned.replaceAll("(?m)^```\\w*\\s*$", "").trim();
        }

        // Usuń JSON wrappery (np. {"content": "..."})
        if (cleaned.startsWith("{") && cleaned.endsWith("}")) {
            try {
                // Spróbuj wyciągnąć pole "content", "markdown", "text" itp
                String extracted = extractFromJsonObject(cleaned);
                if (extracted != null && !extracted.isEmpty()) {
                    cleaned = extracted;
                }
            } catch (Exception e) {
                // Ignoruj błędy, użyj oryginalnego textu
            }
        }

        // Usuń znaki escape
        cleaned = cleaned.replace("\\\"", "\"")
                        .replace("\\n", "\n")
                        .replace("\\\\", "\\");

        return cleaned.trim();
    }

    /**
     * Próbuje wyciągnąć Markdown z JSON obiektu szukając pól takich jak "content", "markdown", "text".
     *
     * @param jsonContent JSON string
     * @return wyciągnięty tekst lub null
     */
    private static String extractFromJsonObject(String jsonContent) {
        // szukaj pól: "content", "markdown", "text", "response", "output"
        String[] fieldNames = {"content", "markdown", "text", "response", "output", "body"};

        for (String field : fieldNames) {
            // Regex: szukaj "fieldName": "value" lub "fieldName":"value"
            String pattern = "\"" + field + "\"\\s*:\\s*\"(.+?)\"";
            java.util.regex.Pattern p = java.util.regex.Pattern.compile(pattern, java.util.regex.Pattern.DOTALL);
            java.util.regex.Matcher m = p.matcher(jsonContent);

            if (m.find()) {
                String value = m.group(1);
                // Odkoduj escape'owany tekst
                return value.replace("\\n", "\n")
                           .replace("\\\"", "\"")
                           .replace("\\\\", "\\");
            }
        }

        return null;
    }

    /**
     * Zwraca fallback prompt do dodania do instrukcji agenta.
     *
     * @return tekst fallback prompta
     */
    public static String getFallbackPrompt() {
        return FALLBACK_PROMPT;
    }

    /**
     * Waliduje czy Markdown ma prawidłową strukturę (zawiera H1 heading).
     *
     * @param markdown tekst do walidacji
     * @return true, jeśli ma prawidłową strukturę
     */
    public static boolean isValidMarkdownStructure(String markdown) {
        if (markdown == null || markdown.isBlank()) {
            return false;
        }

        // Sprawdź czy ma H1 heading na początku
        java.util.regex.Pattern h1Pattern = java.util.regex.Pattern.compile("(?m)^#\\s+.+");
        java.util.regex.Matcher matcher = h1Pattern.matcher(markdown);

        return matcher.find();
    }

    /**
     * Próbuje naprawić Markdown dodając fallback nagłówek H1, jeśli go brakuje.
     *
     * @param markdown tekst do naprawy
     * @param fallbackTitle tytuł fallback (jeśli brakuje H1)
     * @return naprawiony Markdown lub oryginalny, jeśli już jest OK
     */
    public static String repairMarkdownWithFallbackTitle(String markdown, String fallbackTitle) {
        if (markdown == null || markdown.isBlank()) {
            return "# " + (fallbackTitle != null ? fallbackTitle : "Untitled") + "\n\nBrak treści.";
        }

        // Sprawdź, czy ma H1
        if (isValidMarkdownStructure(markdown)) {
            return markdown;
        }

        // Dodaj fallback H1
        String title = fallbackTitle != null && !fallbackTitle.isBlank() ? fallbackTitle : "Untitled";
        return "# " + title + "\n\n" + markdown;
    }
}


