package com.frietsync.backend.dto.community;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

public record CommentResponse(
        UUID id,
        UUID postId,
        UUID authorId,
        String authorName,
        String content,
        Instant editedAt,
        Map<String, Long> reactions,
        String myReaction,
        Instant createdAt
) {
}