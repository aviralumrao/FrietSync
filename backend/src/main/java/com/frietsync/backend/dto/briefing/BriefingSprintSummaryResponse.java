package com.frietsync.backend.dto.briefing;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDate;
import java.util.UUID;

@Data
@Builder
public class BriefingSprintSummaryResponse {
    private UUID sprintId;
    private UUID projectId;
    private String name;
    private String status;
    private LocalDate startDate;
    private LocalDate endDate;
}
