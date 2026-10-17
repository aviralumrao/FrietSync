package com.frietsync.backend.dto.activity;

import com.frietsync.backend.dto.issue.IssueSummaryResponse;
import lombok.Builder;
import lombok.Data;

import java.util.List;
import java.util.UUID;

@Data
@Builder
public class PresenceResponse {

    private UUID userId;
    private String name;
    private List<IssueSummaryResponse> workingOn;
}