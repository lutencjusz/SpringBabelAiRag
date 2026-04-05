package com.example.spring_babel_rag.error;

import org.junit.jupiter.api.Test;

import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

class ResilientExecutorTest {

    @Test
    void shouldReturnResultOnFirstSuccess() throws Exception {
        ResilientExecutor executor = new ResilientExecutor(RetryPolicy.defaults());

        String result = executor.executeWithRetry(() -> "success", "test");

        assertEquals("success", result);
    }

    @Test
    void shouldRetryOnTransientError() throws Exception {
        RetryPolicy policy = new RetryPolicy(3, 10L, 1.5);
        ResilientExecutor executor = new ResilientExecutor(policy);

        AtomicInteger callCount = new AtomicInteger(0);

        String result = executor.executeWithRetry(() -> {
            int count = callCount.incrementAndGet();
            if (count < 3) {
                throw new RuntimeException("Connection timeout");
            }
            return "success after retries";
        }, "test transient");

        assertEquals("success after retries", result);
        assertEquals(3, callCount.get());
    }

    @Test
    void shouldNotRetryOnFormatError() {
        RetryPolicy policy = new RetryPolicy(3, 10L, 1.5);
        ResilientExecutor executor = new ResilientExecutor(policy);

        AtomicInteger callCount = new AtomicInteger(0);

        assertThrows(Exception.class, () -> executor.executeWithRetry(() -> {
            callCount.incrementAndGet();
            throw new com.fasterxml.jackson.core.JsonParseException(null, "invalid json");
        }, "test format error"));

        // Powinien być wywołany tylko raz - bez retry dla błędów formatu
        assertEquals(1, callCount.get());
    }

    @Test
    void shouldUseFallbackOnFormatError() throws Exception {
        RetryPolicy policy = new RetryPolicy(3, 10L, 1.5);
        ResilientExecutor executor = new ResilientExecutor(policy);

        String result = executor.executeWithRetryAndFormatFallback(
                () -> {
                    throw new com.fasterxml.jackson.core.JsonParseException(null, "invalid json");
                },
                () -> "fallback result",
                "test format fallback"
        );

        assertEquals("fallback result", result);
    }

    @Test
    void shouldThrowAfterMaxRetriesExhausted() {
        RetryPolicy policy = new RetryPolicy(2, 10L, 1.5);
        ResilientExecutor executor = new ResilientExecutor(policy);

        AtomicInteger callCount = new AtomicInteger(0);

        assertThrows(Exception.class, () -> executor.executeWithRetry(() -> {
            callCount.incrementAndGet();
            throw new RuntimeException("503 Service Unavailable");
        }, "test max retries"));

        // 1 główna + 2 retry = 3 wywołania łącznie
        assertEquals(3, callCount.get());
    }

    @Test
    void shouldThrowWhenBothMainAndFallbackFail() {
        RetryPolicy policy = new RetryPolicy(1, 10L, 1.5);
        ResilientExecutor executor = new ResilientExecutor(policy);

        assertThrows(Exception.class, () -> executor.executeWithRetryAndFormatFallback(
                () -> {
                    throw new com.fasterxml.jackson.core.JsonParseException(null, "bad json");
                },
                () -> {
                    throw new RuntimeException("fallback also failed");
                },
                "test both fail"
        ));
    }
}

