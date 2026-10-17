package com.frietsync.backend.config.integration;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

@Getter
@Setter
@NoArgsConstructor
@ConfigurationProperties(prefix = "google.oauth")
public class GoogleOAuthProperties {
    private String clientId;
    private String clientSecret;
    private String redirectUri;
    private long stateTtlSeconds = 600;
    private String encryptionKey;
    private String frontendResultUri;
}
