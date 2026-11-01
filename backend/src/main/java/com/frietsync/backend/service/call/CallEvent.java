package com.frietsync.backend.service.call;

import com.frietsync.backend.dto.call.CallParticipantResponse;
import com.frietsync.backend.entity.call.CallSession;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record CallEvent(
        UUID projectId,
        String projectName,
        Type type,
        UUID actorId,
        String actorName,
        CallDetails call,
        List<CallParticipantResponse> participants,
        Instant occurredAt
) {

    public record CallDetails(
            UUID id,
            long roomId,
            UUID startedById,
            String startedByName,
            CallSession.CallStatus status,
            Instant startedAt,
            Instant endedAt
    ) { }

    public enum Type {
        STARTED,
        JOINED,
        LEFT,
        ENDED
    }
}