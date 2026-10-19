package com.frietsync.backend.dto.call;

import com.frietsync.backend.entity.call.CallSession;

import java.time.Instant;
import java.util.UUID;

public record JoinCallResponse(
        UUID id,
        UUID projectId,
        long roomId,
        String roomPin,
        UUID initiatedBy,
        CallSession.CallStatus status,
        Instant startedAt,
        Instant endedAt
) {

    public static JoinCallResponse from(CallSession call, String roomPin) {
        return new JoinCallResponse(
                call.getId(),
                call.getProjectId(),
                call.getRoomId(),
                roomPin,
                call.getInitiatedBy(),
                call.getStatus(),
                call.getStartedAt(),
                call.getEndedAt()
        );
    }
}
