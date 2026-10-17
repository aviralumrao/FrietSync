package com.frietsync.backend.service.replay;

import com.frietsync.backend.dto.replay.ReplayPhaseResponse;
import com.frietsync.backend.dto.replay.ReplaySnapshotResponse;
import com.frietsync.backend.dto.replay.ReplayTimelineResponse;
import com.frietsync.backend.dto.replay.UpdateReplayPhaseRequest;

import java.time.Instant;
import java.util.UUID;

public interface ReplayService {

    ReplayTimelineResponse timeline(UUID projectId, UUID userId);

    ReplayPhaseResponse updatePhase(UUID projectId, UUID phaseId, UpdateReplayPhaseRequest request, UUID userId);

    ReplaySnapshotResponse snapshot(UUID projectId, Instant at, UUID userId);
}