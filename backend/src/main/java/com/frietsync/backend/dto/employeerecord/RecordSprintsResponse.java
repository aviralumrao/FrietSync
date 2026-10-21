package com.frietsync.backend.dto.employeerecord;

import lombok.Builder;
import lombok.Data;

import java.util.List;
import java.util.UUID;

@Data
@Builder
public class RecordSprintsResponse {

    private UUID userId;
    private SprintSummary summary;
    private List<RecordSprintItem> items;
}