package com.frietsync.backend.config.integration;

import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.boot.context.properties.ConfigurationProperties;

@Data
@NoArgsConstructor
@ConfigurationProperties(prefix = "github.oauth")
public class GitHubOAuthProperties {
    private String clientId;
    private String clientSecret;
    private String redirectUri;
    private long stateTtlSeconds = 600;
    private String encryptionKey;
    private String frontendResultUri;
}
