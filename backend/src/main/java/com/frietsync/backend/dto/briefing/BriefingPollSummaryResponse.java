package com.frietsync.backend.dto.briefing;

import lombok.Builder;
import lombok.Data;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

@Data
@Builder
public class BriefingPollSummaryResponse {
    private UUID pollId;
    private UUID projectId;
    private String question;
    private String status;
    private Instant endsAt;
}
