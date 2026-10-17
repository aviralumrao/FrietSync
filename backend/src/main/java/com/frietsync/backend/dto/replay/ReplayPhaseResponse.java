package com.frietsync.backend.dto.replay;

import com.frietsync.backend.entity.replay.ReplayPhaseType;
import lombok.Builder;
import lombok.Data;

import java.time.Instant;
import java.util.UUID;

@Data
@Builder
public class ReplayPhaseResponse {

    private UUID id;
    private ReplayPhaseType phaseType;
    private String title;
    private String description;
    private Instant phaseDate;
    private boolean reached;
    private boolean dateOverridden;
    private int count;
}