package com.frietsync.backend.dto.replay;

import lombok.Builder;
import lombok.Data;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Data
@Builder
public class ReplayTimelineResponse {

    private UUID projectId;
    private String projectName;
    private Instant from;
    private Instant to;
    private List<ReplayPhaseResponse> phases;
}