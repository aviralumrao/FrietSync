package com.frietsync.backend.dto.briefing;

import lombok.Builder;
import lombok.Data;

import java.time.Instant;
import java.util.UUID;

@Data
@Builder
public class BriefingInviteSummaryResponse {
    private UUID inviteId;
    private String email;
    private String role;
    private String purpose;
    private UUID projectId;
    private Instant expiresAt;
}
