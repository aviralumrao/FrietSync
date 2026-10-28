package com.frietsync.backend.dto.community;

import java.util.UUID;

public record CommunityEvent(
        String type,
        UUID workspaceId,
        UUID postId,
        UUID commentId,
        UUID actorId
) {
}