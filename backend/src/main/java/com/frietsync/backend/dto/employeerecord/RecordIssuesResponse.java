package com.frietsync.backend.dto.employeerecord;

import lombok.Builder;
import lombok.Data;

import java.util.List;
import java.util.UUID;

@Data
@Builder
public class RecordIssuesResponse {

    private UUID userId;
    private IssueSummary summary;
    private List<RecordIssueItem> items;
}