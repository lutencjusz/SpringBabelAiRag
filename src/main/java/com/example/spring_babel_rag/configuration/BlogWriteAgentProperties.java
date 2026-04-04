package com.example.spring_babel_rag.configuration;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "blog-write-agent")
public record BlogWriteAgentProperties(String outputDir) {

    public BlogWriteAgentProperties {
        if (outputDir== null || outputDir.isBlank()) {
            outputDir = "blog-posts";
        }

    }

}
