package com.frietsync.backend.service.impl.project;

import com.frietsync.backend.dto.project.MilestoneRequest;
import com.frietsync.backend.dto.project.MilestoneResponse;
import com.frietsync.backend.dto.project.UpdateMilestoneRequest;
import com.frietsync.backend.entity.project.Milestone;
import com.frietsync.backend.entity.project.Project;
import com.frietsync.backend.entity.sprint.Sprint;
import com.frietsync.backend.entity.sprint.SprintStatus;
import com.frietsync.backend.exception.BadRequestException;
import com.frietsync.backend.exception.ForbiddenException;
import com.frietsync.backend.exception.ResourceNotFoundException;
import com.frietsync.backend.repository.project.MilestoneRepository;
import com.frietsync.backend.repository.project.ProjectMemberRepository;
import com.frietsync.backend.repository.project.ProjectRepository;
import com.frietsync.backend.repository.sprint.SprintRepository;
import com.frietsync.backend.service.project.MilestoneService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class MilestoneServiceImpl implements MilestoneService {

    private final MilestoneRepository milestoneRepository;
    private final ProjectRepository projectRepository;
    private final ProjectMemberRepository projectMemberRepository;
    private final SprintRepository sprintRepository;
    private final ProjectAccess projectAccess;

    @Override
    @Transactional
    public MilestoneResponse create(UUID projectId, UUID userId, MilestoneRequest request) {
        Project project = findProject(projectId);
        requireProjectManager(project, userId);

        Milestone milestone = new Milestone();
        milestone.setProjectId(projectId);
        milestone.setName(request.getName().trim());
        milestone.setDescription(request.getDescription());
        milestone.setDueDate(request.getDueDate());
        return toResponse(milestoneRepository.save(milestone));
    }

    @Override
    @Transactional(readOnly = true)
    public List<MilestoneResponse> list(UUID projectId, UUID userId) {
        Project project = findProject(projectId);
        requireProjectMember(project, userId);

        List<MilestoneResponse> responses = new ArrayList<>();
        for (Milestone milestone : milestoneRepository.findByProjectIdOrderByDueDateAsc(projectId)) {
            responses.add(toResponse(milestone));
        }
        return responses;
    }

    @Override
    @Transactional
    public MilestoneResponse update(UUID milestoneId, UUID userId, UpdateMilestoneRequest request) {
        Milestone milestone = findMilestone(milestoneId);
        requireProjectManager(findProject(milestone.getProjectId()), userId);

        if (request.getName() != null) {
            if (!StringUtils.hasText(request.getName()) || request.getName().length() > 200) {
                throw new BadRequestException("Milestone name must contain 1 to 200 characters");
            }
            milestone.setName(request.getName().trim());
        }
        if (request.getDescription() != null) {
            if (request.getDescription().length() > 2000) {
                throw new BadRequestException("Milestone description cannot exceed 2000 characters");
            }
            milestone.setDescription(request.getDescription());
        }
        if (request.getDueDate() != null) {
            milestone.setDueDate(request.getDueDate());
        }
        return toResponse(milestoneRepository.save(milestone));
    }

    @Override
    @Transactional
    public void delete(UUID milestoneId, UUID userId) {
        Milestone milestone = findMilestone(milestoneId);
        requireProjectManager(findProject(milestone.getProjectId()), userId);
        List<Sprint> sprints = sprintRepository.findByMilestoneId(milestoneId);
        for (Sprint sprint : sprints) {
            sprint.setMilestoneId(null);
        }
        sprintRepository.saveAll(sprints);
        milestoneRepository.delete(milestone);
    }

    private MilestoneResponse toResponse(Milestone milestone) {
        long totalSprints = sprintRepository.countByMilestoneId(milestone.getId());
        long completedSprints = sprintRepository.countByMilestoneIdAndStatus(
                milestone.getId(), SprintStatus.COMPLETED);
        double progress = 0;
        if (totalSprints > 0) {
            progress = completedSprints * 100.0 / totalSprints;
        }
        return MilestoneResponse.fromEntity(milestone, progress);
    }

    private Milestone findMilestone(UUID milestoneId) {
        return milestoneRepository.findById(milestoneId)
                .orElseThrow(() -> new ResourceNotFoundException("Milestone not found"));
    }

    private Project findProject(UUID projectId) {
        return projectRepository.findById(projectId)
                .orElseThrow(() -> new ResourceNotFoundException("Project not found"));
    }

    private void requireProjectManager(Project project, UUID userId) {
        projectAccess.requireWorkspaceAndActive(project, userId);
        if (userId == null || !userId.equals(project.getProjectManagerId())) {
            throw new ForbiddenException("Only the project manager can manage milestones");
        }
    }

    private void requireProjectMember(Project project, UUID userId) {
        projectAccess.requireWorkspaceAndActive(project, userId);
        boolean projectLeader = userId != null && (userId.equals(project.getCreatedBy())
                || userId.equals(project.getAdminId())
                || userId.equals(project.getProjectManagerId())
                || userId.equals(project.getTeamLeadId()));
        if (!projectLeader && !projectMemberRepository.existsByProjectIdAndUserId(project.getId(), userId)) {
            throw new ForbiddenException("You don't have access to this project's milestones");
        }
    }
}
