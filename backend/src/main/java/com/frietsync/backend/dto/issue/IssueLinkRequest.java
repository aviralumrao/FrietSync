package com.frietsync.backend.dto.issue;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.util.UUID;

@Data
public class IssueLinkRequest {

    @NotNull(message = "targetIssueId is required")
    private UUID targetIssueId;

}