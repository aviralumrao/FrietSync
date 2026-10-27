package com.frietsync.backend.dto.community;

import java.time.Instant;
import java.util.UUID;

public record PostResponse(
        UUID id,
        UUID workspaceId,
        UUID authorId,
        String authorName,
        String title,
        String content,
        boolean pinned,
        Instant pinnedAt,
        Instant editedAt,
        long commentCount,
        Instant createdAt,
        Instant updatedAt
) {
}