package com.example.spring_babel_rag.configuration;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "retry-policy")
public record RetryPolicyProperties(
        int maxRetries,
        long initialDelayMs,
        double backoffMultiplier
) {
    public RetryPolicyProperties {
        if (maxRetries < 0) {
            maxRetries = 3;
        }
        if (initialDelayMs < 0) {
            initialDelayMs = 1000L;
        }
        if (backoffMultiplier < 1.0) {
            backoffMultiplier = 2.0;
        }
    }
}

