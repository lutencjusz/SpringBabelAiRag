package com.example.spring_babel_rag.error;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

/**
 * Wrapper do wykonywania funkcji z warunkowym retry i fallback handlerami.
 * Obsługuje:
 * - Retry warunkowe (tylko dla błędów przejściowych)
 * - Fallback dla błędów formatu
 */
public class ResilientExecutor {

    private static final Log log = LogFactory.getLog(ResilientExecutor.class);

    private final RetryPolicy retryPolicy;

    public ResilientExecutor(RetryPolicy retryPolicy) {
        this.retryPolicy = retryPolicy;
    }

    public ResilientExecutor() {
        this(RetryPolicy.defaults());
    }

    /**
     * Wykonuje funkcję z warunkowym retry dla błędów przejściowych.
     *
     * @param <T> typ wyniku
     * @param function funkcja do wykonania
     * @param description opis operacji (do logowania)
     * @return wynik funkcji
     * @throws Exception jeśli wszystkie próby się nie powiodły
     */
    public <T> T executeWithRetry(AiFunction<T> function, String description) throws Exception {
        int attempts = 0;
        Exception lastException = null;

        while (attempts <= retryPolicy.getMaxRetries()) {
            try {
                log.debug(description + " - próba " + (attempts + 1));
                return function.execute();

            } catch (Exception e) {
                lastException = e;

                // Sprawdź, czy błąd jest przejściowy
                if (!retryPolicy.isRetryableError(e)) {
                    log.warn(description + " - błąd permanentny (bez retry): " + e.getMessage());
                    throw e;
                }

                attempts++;

                if (attempts > retryPolicy.getMaxRetries()) {
                    log.warn(description + " - maksymalna liczba prób osiągnięta");
                    throw e;
                }

                long delayMs = retryPolicy.getDelayForAttempt(attempts - 1);
                log.warn(description + " - błąd przejściowy, retry za " + delayMs + "ms: " + e.getMessage());

                Thread.sleep(delayMs);
            }
        }

        assert lastException != null;
        throw lastException;
    }

    /**
     * Wykonuje funkcję z retry i fallback handler-ami dla błędów formatu.
     *
     * @param <T> typ wyniku
     * @param function funkcja główna
     * @param fallbackFunction funkcja fallback (alternatywny prompt/parser)
     * @param description opis operacji
     * @return wynik funkcji
     * @throws Exception jeśli obie funkcje się nie powiodły
     */
    public <T> T executeWithRetryAndFormatFallback(AiFunction<T> function, AiFunction<T> fallbackFunction, String description) throws Exception {
        try {
            // Spróbuj główną funkcję z retry
            return executeWithRetry(function, description);

        } catch (Exception e) {
            // Jeśli to błąd formatu, spróbuj fallback
            if (FormatErrorHandler.isFormatError(e)) {
                log.warn(description + " - błąd formatu, próbę fallback prompt: " + e.getMessage());
                try {
                    return executeWithRetry(fallbackFunction, description + " (fallback)");
                } catch (Exception fallbackException) {
                    log.error(description + " - fallback też się nie powiódł: " + fallbackException.getMessage());
                    throw fallbackException;
                }
            }

            // Nie jest błędem formatu - rzuć oryginalny wyjątek
            throw e;
        }
    }

    /**
     * Wrapper dla funkcji, które mogą zwrócić String - z fallback czyszczeniem formatu.
     *
     * @param function główna funkcja
     * @param description opis operacji
     * @return wynik lub naprawiony wynik
     * @throws Exception jeśli obie próby się nie powiodły
     */
    public String executeStringWithFormatRecovery(AiFunction<String> function, String description) throws Exception {
        try {
            return executeWithRetry(function, description);

        } catch (Exception e) {
            if (FormatErrorHandler.isFormatError(e)) {
                log.warn(description + " - nie udało się parsować wyniku, próba fallback czyszczenia");
                // W tym wypadku rethrow - fallback wymaga callback funkcji od agenta
                throw e;
            }
            throw e;
        }
    }

    /**
     * Interfejs funkcji do wykonania przez executor.
     */
    @FunctionalInterface
    public interface AiFunction<T> {
        T execute() throws Exception;
    }

    public RetryPolicy getRetryPolicy() {
        return retryPolicy;
    }
}

