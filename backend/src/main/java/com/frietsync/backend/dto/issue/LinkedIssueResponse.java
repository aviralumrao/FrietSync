package com.frietsync.backend.dto.issue;

import lombok.Builder;
import lombok.Data;

import java.util.UUID;

@Data
@Builder
public class LinkedIssueResponse {
    private UUID linkId;
    private UUID issueId;
    private int issueNumber;
    private String issueTitle;
}
