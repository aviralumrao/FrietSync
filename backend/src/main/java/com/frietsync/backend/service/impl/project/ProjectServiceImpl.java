package com.frietsync.backend.service.impl.project;

import com.frietsync.backend.dto.project.ProjectRequest;
import com.frietsync.backend.dto.project.ProjectResponse;
import com.frietsync.backend.entity.project.Project;
import com.frietsync.backend.entity.project.ProjectStatus;
import com.frietsync.backend.entity.user.User;
import com.frietsync.backend.exception.BadRequestException;
import com.frietsync.backend.exception.ForbiddenException;
import com.frietsync.backend.exception.ResourceNotFoundException;
import com.frietsync.backend.repository.project.ProjectRepository;
import com.frietsync.backend.repository.user.UserRepository;
import com.frietsync.backend.service.project.ProjectService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import java.time.LocalDate;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import java.util.stream.Stream;

@Service
@RequiredArgsConstructor
public class ProjectServiceImpl implements ProjectService {

    private final ProjectRepository projectRepository;
    private final UserRepository userRepository;

    @Override
    public ProjectResponse create(ProjectRequest request, UUID userId) {
        Project project = new Project();
        project.setCreatedBy(userId);
        project.setAdminId(userId);
        apply(project, request);
        return ProjectResponse.fromEntity(projectRepository.save(project));
    }
    @Override
    public List<ProjectResponse> myProjects(UUID userId) {
        return projectRepository.findAllForUser(userId).stream()
                .map(ProjectResponse::fromEntity)
                .toList();
    }
    @Override
    public ProjectResponse get(UUID projectId, UUID userId) {
        Project project = find(projectId);
        if (!isInvolved(project, userId)) {
            throw new ForbiddenException("You don't have access to this project");
        }
        return ProjectResponse.fromEntity(project);
    }

    @Override
    public ProjectResponse update(UUID projectId, ProjectRequest request, UUID userId) {
        Project project = find(projectId);
        boolean canEdit = userId.equals(project.getAdminId())
                || userId.equals(project.getProjectManagerId());
        if (!canEdit) {
            throw new ForbiddenException("Only the admin or project manager can edit this project");
        }
        apply(project, request);
        return ProjectResponse.fromEntity(projectRepository.save(project));
    }

    @Override
    public void delete(UUID projectId, UUID userId) {
        Project project = find(projectId);
        if (!userId.equals(project.getAdminId())) {
            throw new ForbiddenException("Only the admin can delete this project");
        }
        projectRepository.delete(project);
    }

    private Project find(UUID projectId) {
        return projectRepository.findById(projectId)
                .orElseThrow(() -> new ResourceNotFoundException("Project not found"));
    }
    private boolean isInvolved(Project p, UUID userId) {
        return Stream.of(p.getCreatedBy(), p.getAdminId(), p.getProjectManagerId(), p.getTeamLeadId())
                .anyMatch(userId::equals);
    }
    private void apply(Project project, ProjectRequest request) {
        if (request.getStartDate() != null && request.getDeadline() != null
                && request.getDeadline().isBefore(request.getStartDate())) {
            throw new BadRequestException("Deadline cannot be before start date");
        }
        project.setName(request.getName().trim());
        project.setDescription(request.getDescription());
        project.setWorkspaceId(request.getWorkspaceId());
        project.setStartDate(request.getStartDate());
        project.setDeadline(request.getDeadline());
        if (request.getStatus() != null) {
            project.setStatus(request.getStatus());
        }
        if (project.getStatus() == ProjectStatus.COMPLETED) {
            if (project.getCompleteDate() == null) {
                project.setCompleteDate(LocalDate.now());
            }
        } else {
            project.setCompleteDate(null);
        }
        project.setProjectManagerId(findUserId(request.getProjectManagerEmail()));
        project.setTeamLeadId(findUserId(request.getTeamLeadEmail()));
        project.setTeamSize((int) Stream.of(project.getAdminId(),
                        project.getProjectManagerId(), project.getTeamLeadId())
                .filter(Objects::nonNull)
                .distinct()
                .count());
    }
    private UUID findUserId(String email) {
        if (email == null || email.isBlank()) {
            return null;
        }
        return userRepository.findByEmail(email.trim())
                .map(User::getId)
                .orElseThrow(() -> new BadRequestException("No user found with email: " + email));
    }
}