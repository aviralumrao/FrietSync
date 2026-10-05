package com.frietsync.backend.service.impl.sprint;

import com.frietsync.backend.dto.sprint.CreateSprintRequest;
import com.frietsync.backend.dto.sprint.SprintResponse;
import com.frietsync.backend.dto.sprint.UpdateSprintRequest;
import com.frietsync.backend.entity.project.Project;
import com.frietsync.backend.entity.sprint.Sprint;
import com.frietsync.backend.entity.sprint.SprintStatus;
import com.frietsync.backend.exception.BadRequestException;
import com.frietsync.backend.exception.ForbiddenException;
import com.frietsync.backend.exception.ResourceNotFoundException;
import com.frietsync.backend.repository.project.ProjectRepository;
import com.frietsync.backend.repository.sprint.SprintRepository;
import com.frietsync.backend.service.project.ProjectService;
import com.frietsync.backend.service.sprint.SprintService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class SprintServiceImpl implements SprintService {

    private final SprintRepository sprintRepository;
    private final ProjectRepository projectRepository;
    private final ProjectService projectService;

    @Override
    public SprintResponse createSprint(UUID projectId, CreateSprintRequest request, UUID currentUserId) {
        requireProjectManager(projectId, currentUserId);
        List<Sprint> existing = sprintRepository.findByProjectId(projectId);
        int nextNumber = existing.size() + 1;

        Sprint sprint = new Sprint();
        sprint.setProjectId(projectId);
        sprint.setSprintNumber(nextNumber);
        sprint.setName(request.getName());
        sprint.setGoal(request.getGoal());
        sprint.setStatus(SprintStatus.PLANNED);
        sprint.setStartDate(request.getStartDate());
        sprint.setEndDate(request.getEndDate());
        sprint.setCreatedBy(currentUserId);

        return SprintResponse.fromEntity(sprintRepository.save(sprint));
    }

    @Override
    public List<SprintResponse> listSprints(UUID projectId, UUID currentUserId) {
        projectService.get(projectId, currentUserId);
        return sprintRepository.findByProjectId(projectId).stream()
                .map(SprintResponse::fromEntity)
                .collect(Collectors.toList());
    }

    @Override
    public SprintResponse getSprint(UUID sprintId, UUID currentUserId) {
        Sprint sprint = findSprintOrThrow(sprintId);
        projectService.get(sprint.getProjectId(), currentUserId);
        return SprintResponse.fromEntity(sprint);
    }

    @Override
    public SprintResponse updateSprint(UUID sprintId, UpdateSprintRequest request, UUID currentUserId) {
        Sprint sprint = findSprintOrThrow(sprintId);
        requireProjectManager(sprint.getProjectId(), currentUserId);

        if (request.getName() != null) sprint.setName(request.getName());
        if (request.getGoal() != null) sprint.setGoal(request.getGoal());
        if (request.getStartDate() != null) sprint.setStartDate(request.getStartDate());
        if (request.getEndDate() != null) sprint.setEndDate(request.getEndDate());

        return SprintResponse.fromEntity(sprintRepository.save(sprint));
    }

    @Override
    public SprintResponse startSprint(UUID sprintId, UUID currentUserId) {
        Sprint sprint = findSprintOrThrow(sprintId);
        requireProjectManager(sprint.getProjectId(), currentUserId);

        if (sprint.getStatus() != SprintStatus.PLANNED) {
            throw new BadRequestException("Only a planned sprint can be started");
        }

        sprint.setStatus(SprintStatus.ACTIVE);
        return SprintResponse.fromEntity(sprintRepository.save(sprint));
    }

    @Override
    public SprintResponse completeSprint(UUID sprintId, UUID currentUserId) {
        Sprint sprint = findSprintOrThrow(sprintId);
        requireProjectManager(sprint.getProjectId(), currentUserId);

        if (sprint.getStatus() != SprintStatus.ACTIVE) {
            throw new BadRequestException("Only an active sprint can be completed");
        }

        sprint.setStatus(SprintStatus.COMPLETED);
        return SprintResponse.fromEntity(sprintRepository.save(sprint));
    }

    @Override
    public void deleteSprint(UUID sprintId, UUID currentUserId) {
        Sprint sprint = findSprintOrThrow(sprintId);
        requireProjectManager(sprint.getProjectId(), currentUserId);
        sprintRepository.delete(sprint);
    }

    private void requireProjectManager(UUID projectId, UUID userId) {
        Project project = projectRepository.findById(projectId)
                .orElseThrow(() -> new ResourceNotFoundException("Project not found"));
        if (userId == null || !userId.equals(project.getProjectManagerId())) {
            throw new ForbiddenException("Only the project manager can perform this action");
        }
    }

    private Sprint findSprintOrThrow(UUID sprintId) {
        return sprintRepository.findById(sprintId)
                .orElseThrow(() -> new ResourceNotFoundException("Sprint not found"));
    }
}