package com.frietsync.backend.dto.sprint;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.util.UUID;

@Data
public class ApproveSprintAssignmentRequest {

    @NotNull
    private UUID requestId;
}
