package com.example.spring_babel_rag.configuration;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

@ConfigurationProperties(prefix = "copilot")
public record CopilotProperties(Duration timeout) {

    public CopilotProperties {
        if (timeout == null || timeout.isZero() || timeout.isNegative()) {
            timeout = Duration.ofSeconds(240);
        }
    }
}

