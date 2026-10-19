package com.frietsync.backend.dto.call;

import com.frietsync.backend.entity.call.CallSession;

import java.time.Instant;
import java.util.UUID;

public record CallResponse(
        UUID id,
        UUID projectId,
        long roomId,
        UUID initiatedBy,
        CallSession.CallStatus status,
        Instant startedAt,
        Instant endedAt
) {

    public static CallResponse from(CallSession call) {
        return new CallResponse(
                call.getId(),
                call.getProjectId(),
                call.getRoomId(),
                call.getInitiatedBy(),
                call.getStatus(),
                call.getStartedAt(),
                call.getEndedAt()
        );
    }
}
