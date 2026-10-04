package com.frietsync.backend.service.sprint;

import com.frietsync.backend.dto.sprint.CreateSprintRequest;
import com.frietsync.backend.dto.sprint.SprintResponse;
import com.frietsync.backend.dto.sprint.UpdateSprintRequest;

import java.util.List;
import java.util.UUID;

public interface SprintService {
    SprintResponse createSprint(UUID projectId, CreateSprintRequest request, UUID currentUserId);
    List<SprintResponse> listSprints(UUID projectId);
    SprintResponse getSprint(UUID sprintId);
    SprintResponse updateSprint(UUID sprintId, UpdateSprintRequest request);
    SprintResponse startSprint(UUID sprintId);
    SprintResponse completeSprint(UUID sprintId);
    void deleteSprint(UUID sprintId);
}