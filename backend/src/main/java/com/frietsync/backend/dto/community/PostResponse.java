package com.frietsync.backend.dto.community;

import java.time.Instant;
import java.util.Map;
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
        Map<String, Long> reactions,
        String myReaction,
        Instant createdAt,
        Instant updatedAt
) {
}