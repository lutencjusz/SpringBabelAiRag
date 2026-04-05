package com.example.spring_babel_rag;

import com.example.spring_babel_rag.configuration.BlogWriteAgentProperties;
import com.example.spring_babel_rag.configuration.RetryPolicyProperties;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;

@EnableConfigurationProperties({BlogWriteAgentProperties.class, RetryPolicyProperties.class})
@SpringBootApplication
public class SpringBabelRagApplication {

	public static void main(String[] args) {
		SpringApplication.run(SpringBabelRagApplication.class, args);
	}

}
