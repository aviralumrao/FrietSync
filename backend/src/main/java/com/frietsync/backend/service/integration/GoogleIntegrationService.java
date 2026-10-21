package com.frietsync.backend.service.integration;

import java.net.URI;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public interface GoogleIntegrationService {
    URI createAuthorizationUri(UUID userId);
    UUID consumeState(String state);
    void complete(UUID userId, String code);
    void disconnect(UUID userId);
    String getValidAccessToken(UUID userId);
    boolean isConnected(UUID userId);
    String createMeetLink(UUID userId, String title, String description,
                          Instant startAt, Instant endAt, List<String> attendeeEmails);
}
