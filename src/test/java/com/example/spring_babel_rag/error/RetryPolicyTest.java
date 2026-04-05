package com.example.spring_babel_rag.error;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class RetryPolicyTest {

    @Test
    void shouldRetryOnTimeoutError() {
        RetryPolicy policy = RetryPolicy.defaults();
        Exception timeoutEx = new RuntimeException("Connection timeout occurred");
        assertTrue(policy.isRetryableError(timeoutEx));
    }

    @Test
    void shouldRetryOnRateLimitError() {
        RetryPolicy policy = RetryPolicy.defaults();
        Exception rateLimitEx = new RuntimeException("HTTP 429 Too Many Requests");
        assertTrue(policy.isRetryableError(rateLimitEx));
    }

    @Test
    void shouldRetryOnServiceUnavailable() {
        RetryPolicy policy = RetryPolicy.defaults();
        Exception serviceEx = new RuntimeException("HTTP 503 Service Unavailable - temporarily unavailable");
        assertTrue(policy.isRetryableError(serviceEx));
    }

    @Test
    void shouldNotRetryOnJsonParseException() {
        RetryPolicy policy = RetryPolicy.defaults();
        // Symulacja JsonParseException przez RuntimeException z nazwą klasy
        Exception formatEx = new com.fasterxml.jackson.core.JsonParseException(null, "Unexpected character");
        assertFalse(policy.isRetryableError(formatEx));
    }

    @Test
    void shouldNotRetryOnIllegalArgumentException() {
        RetryPolicy policy = RetryPolicy.defaults();
        Exception argEx = new IllegalArgumentException("invalid format: missing field");
        assertFalse(policy.isRetryableError(argEx));
    }

    @Test
    void shouldNotRetryOnNullException() {
        RetryPolicy policy = RetryPolicy.defaults();
        assertFalse(policy.isRetryableError(null));
    }

    @Test
    void shouldCalculateExponentialBackoff() {
        RetryPolicy policy = new RetryPolicy(3, 1000L, 2.0);
        assertEquals(1000L, policy.getDelayForAttempt(0));
        assertEquals(2000L, policy.getDelayForAttempt(1));
        assertEquals(4000L, policy.getDelayForAttempt(2));
    }

    @Test
    void defaultsFactoryShouldHaveCorrectValues() {
        RetryPolicy policy = RetryPolicy.defaults();
        assertEquals(3, policy.getMaxRetries());
        assertEquals(1000L, policy.getInitialDelayMs());
        assertEquals(2.0, policy.getBackoffMultiplier());
    }
}

