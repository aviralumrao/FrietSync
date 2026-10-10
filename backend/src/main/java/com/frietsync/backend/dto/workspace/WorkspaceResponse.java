package com.frietsync.backend.dto.workspace;

import java.time.Instant;
import java.util.UUID;

public record WorkspaceResponse(
        UUID id,
        String name,
        UUID adminId,
        Instant createdOn
) {
}