package com.example.spring_babel_rag.configuration;

import com.example.spring_babel_rag.error.ResilientExecutor;
import com.example.spring_babel_rag.error.RetryPolicy;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableConfigurationProperties(RetryPolicyProperties.class)
public class ResilientExecutorConfig {

    @Bean
    public ResilientExecutor resilientExecutor(RetryPolicyProperties retryPolicyProperties) {
        RetryPolicy retryPolicy = new RetryPolicy(
                retryPolicyProperties.maxRetries(),
                retryPolicyProperties.initialDelayMs(),
                retryPolicyProperties.backoffMultiplier()
        );
        return new ResilientExecutor(retryPolicy);
    }
}

