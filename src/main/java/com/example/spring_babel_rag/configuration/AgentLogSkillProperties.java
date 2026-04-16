package com.example.spring_babel_rag.configuration;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "agent-log-skill")
public record AgentLogSkillProperties(String sourceLogPath, String outputDir, int maxTableRows) {

    public AgentLogSkillProperties {
        if (sourceLogPath == null || sourceLogPath.isBlank()) {
            sourceLogPath = "C:/logs/intelijLog.txt";
        }
        if (outputDir == null || outputDir.isBlank()) {
            outputDir = "reports";
        }
        if (maxTableRows <= 0) {
            maxTableRows = 25;
        }
    }
}

