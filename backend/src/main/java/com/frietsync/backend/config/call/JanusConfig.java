package com.frietsync.backend.config.call;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "janus")
public record JanusConfig(
        String baseUrl,
        String adminKey,
        String roomSecret,
        String roomPin
) {
}
