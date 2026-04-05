package com.example.spring_babel_rag.error;

/**
 * Polityka warunkowego retry dla błędów przejściowych.
 * Rozróżnia między błędami przejściowymi (do ponowienia) a permanentnymi (bez retry).
 */
public class RetryPolicy {

    private final int maxRetries;
    private final long initialDelayMs;
    private final double backoffMultiplier;

    public RetryPolicy(int maxRetries, long initialDelayMs, double backoffMultiplier) {
        this.maxRetries = maxRetries;
        this.initialDelayMs = initialDelayMs;
        this.backoffMultiplier = backoffMultiplier;
    }

    public static RetryPolicy defaults() {
        return new RetryPolicy(3, 1000L, 2.0);
    }

    public static RetryPolicy aggressive() {
        return new RetryPolicy(5, 500L, 1.5);
    }

    public static RetryPolicy conservative() {
        return new RetryPolicy(1, 500L, 1.0);
    }

    /**
     * Sprawdza, czy błąd jest przejściowy i można go ponowić.
     *
     * @param throwable wyjątek do sprawdzenia
     * @return true jeśli błąd jest przejściowy, false jeśli permanentny
     */
    public boolean isRetryableError(Throwable throwable) {
        if (throwable == null) {
            return false;
        }

        String message = throwable.getMessage() != null ? throwable.getMessage().toLowerCase() : "";
        String className = throwable.getClass().getSimpleName();

        // Błędy przejściowe - można retry
        if (className.contains("TimeoutException") ||
            className.contains("SocketTimeoutException") ||
            message.contains("timeout") ||
            message.contains("connection refused") ||
            message.contains("connection reset") ||
            message.contains("temporarily unavailable") ||
            message.contains("429") || // Rate limit
            message.contains("503") || // Service unavailable
            message.contains("502") || // Bad gateway
            message.contains("504") || // Gateway timeout
            message.contains("50[0-9]")) { // 5xx errors
            return true;
        }

        // Błędy permanentne - bez retry
        if (className.contains("JsonParseException") ||
            className.contains("JsonMappingException") ||
            className.contains("ParseException") ||
            className.contains("ValidationException") ||
            className.contains("IllegalArgumentException") ||
            message.contains("malformed") ||
            message.contains("syntax error") ||
            message.contains("invalid format")) {
            return false;
        }

        // Błędy sieciowe - można retry
        return className.contains("IOException") ||
               className.contains("NetworkException") ||
               className.contains("ConnectException");
    }

    /**
     * Oblicza opóźnienie dla danego numeru próby z exponential backoff.
     *
     * @param attemptNumber numer próby (0-based)
     * @return opóźnienie w milisekundach
     */
    public long getDelayForAttempt(int attemptNumber) {
        if (attemptNumber <= 0) {
            return initialDelayMs;
        }
        return Math.round(initialDelayMs * Math.pow(backoffMultiplier, attemptNumber));
    }

    public int getMaxRetries() {
        return maxRetries;
    }

    public long getInitialDelayMs() {
        return initialDelayMs;
    }

    public double getBackoffMultiplier() {
        return backoffMultiplier;
    }
}

