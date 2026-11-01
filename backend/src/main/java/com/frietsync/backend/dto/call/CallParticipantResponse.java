package com.frietsync.backend.dto.call;

import java.time.Instant;
import java.util.UUID;

public record CallParticipantResponse(
        UUID userId,
        String userName,
        Instant joinedAt,
        Instant lastActiveAt
) {
}
