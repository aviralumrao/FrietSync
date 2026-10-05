package com.frietsync.backend.service.sprint;

import com.frietsync.backend.dto.sprint.CreateSprintRequest;
import com.frietsync.backend.dto.sprint.SprintAssignmentRequestResponse;
import com.frietsync.backend.dto.sprint.SprintResponse;
import com.frietsync.backend.dto.sprint.UpdateSprintRequest;

import java.util.List;
import java.util.UUID;

public interface SprintService {
    SprintResponse createSprint(UUID projectId, CreateSprintRequest request, UUID currentUserId);
    List<SprintResponse> listSprints(UUID projectId, UUID currentUserId);
    SprintResponse getSprint(UUID sprintId, UUID currentUserId);
    SprintResponse updateSprint(UUID sprintId, UpdateSprintRequest request, UUID currentUserId);
    SprintResponse startSprint(UUID sprintId, UUID currentUserId);
    SprintResponse completeSprint(UUID sprintId, UUID currentUserId);
    void deleteSprint(UUID sprintId, UUID currentUserId);
    SprintResponse assignSprint(UUID sprintId, UUID contributorId, UUID currentUserId);
    SprintAssignmentRequestResponse requestAssignment(UUID sprintId, UUID currentUserId);
    List<SprintAssignmentRequestResponse> listSprintRequests(UUID projectId, UUID currentUserId);
    SprintAssignmentRequestResponse approveRequest(
            UUID sprintId,
            UUID requestId,
            UUID currentUserId);
}