package com.frietsync.backend.config.integration;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestTemplate;
import com.frietsync.backend.service.integration.GitHubApiClient;

@Configuration
@EnableConfigurationProperties(GitHubOAuthProperties.class)
public class GitHubOAuthConfig {
    @Bean
    public GitHubApiClient gitHubApiClient() {
        return new GitHubApiClient(new RestTemplate());
    }
}
