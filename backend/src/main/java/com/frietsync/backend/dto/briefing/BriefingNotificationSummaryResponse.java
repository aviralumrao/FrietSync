package com.frietsync.backend.dto.briefing;

import lombok.Builder;
import lombok.Data;

import java.time.Instant;
import java.util.UUID;

@Data
@Builder
public class BriefingNotificationSummaryResponse {
    private UUID notificationId;
    private String title;
    private String message;
    private String type;
    private boolean read;
    private Instant createdAt;
}
