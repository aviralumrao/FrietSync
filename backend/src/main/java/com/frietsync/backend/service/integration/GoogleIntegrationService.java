package com.frietsync.backend.service.integration;

import java.net.URI;
import java.util.UUID;

public interface GoogleIntegrationService {
    URI createAuthorizationUri(UUID userId);
    UUID consumeState(String state);
    void complete(UUID userId, String code);
    void disconnect(UUID userId);
    String getValidAccessToken(UUID userId);
}
