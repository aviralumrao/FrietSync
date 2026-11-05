package com.frietsync.backend.service.impl.project;

import com.frietsync.backend.dto.project.MilestoneRequest;
import com.frietsync.backend.dto.project.MilestoneResponse;
import com.frietsync.backend.dto.project.UpdateMilestoneRequest;
import com.frietsync.backend.entity.project.Milestone;
import com.frietsync.backend.entity.project.Project;
import com.frietsync.backend.entity.issue.Issue;
import com.frietsync.backend.entity.issue.IssueStatus;
import com.frietsync.backend.exception.BadRequestException;
import com.frietsync.backend.exception.ForbiddenException;
import com.frietsync.backend.exception.ResourceNotFoundException;
import com.frietsync.backend.repository.project.MilestoneRepository;
import com.frietsync.backend.repository.issue.IssueRepository;
import com.frietsync.backend.repository.project.ProjectMemberRepository;
import com.frietsync.backend.repository.project.ProjectRepository;
import com.frietsync.backend.service.project.MilestoneService;
import com.frietsync.backend.service.impl.activity.ProjectActivityRecorder;
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
    private final IssueRepository issueRepository;
    private final ProjectAccess projectAccess;
    private final ProjectActivityRecorder activityRecorder;

    @Override
    @Transactional
    public MilestoneResponse create(UUID projectId, UUID userId, MilestoneRequest request) {
        Project project = findProject(projectId);
        projectAccess.requireActiveProject(project);
        requireProjectManager(project, userId);
        validateDueDate(project, request.getDueDate());

        Milestone milestone = new Milestone();
        milestone.setProjectId(projectId);
        milestone.setName(request.getName().trim());
        milestone.setDescription(request.getDescription());
        milestone.setDueDate(request.getDueDate());
        Milestone savedMilestone = milestoneRepository.save(milestone);
        activityRecorder.record(projectId, userId, "MILESTONE", savedMilestone.getId(),
                savedMilestone.getName(), "CREATED");
        return toResponse(savedMilestone);
    }

    @Override
    @Transactional(readOnly = true)
    public List<MilestoneResponse> list(UUID projectId, UUID userId) {
        Project project = findProject(projectId);
        requireProjectMember(project, userId);

        List<MilestoneResponse> responses = new ArrayList<>();
        for (Milestone milestone : milestoneRepository.findByProjectIdAndDeletedAtIsNullOrderByDueDateAsc(projectId)) {
            responses.add(toResponse(milestone));
        }
        return responses;
    }

    @Override
    @Transactional
    public MilestoneResponse update(UUID milestoneId, UUID userId, UpdateMilestoneRequest request) {
        Milestone milestone = findMilestone(milestoneId);
        Project project = findProject(milestone.getProjectId());
        projectAccess.requireActiveProject(project);
        requireProjectManager(project, userId);

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
            validateDueDate(project, request.getDueDate());
            for (Issue issue : issueRepository.findByMilestoneIdAndDeletedAtIsNull(milestoneId)) {
                if ((issue.getStartDate() != null && issue.getStartDate().isAfter(request.getDueDate()))
                        || (issue.getDueDate() != null && issue.getDueDate().isAfter(request.getDueDate()))) {
                    throw new BadRequestException("Issue dates cannot be after the milestone due date");
                }
            }
            milestone.setDueDate(request.getDueDate());
        }
        Milestone updatedMilestone = milestoneRepository.save(milestone);
        activityRecorder.record(updatedMilestone.getProjectId(), userId, "MILESTONE", updatedMilestone.getId(),
                updatedMilestone.getName(), "UPDATED");
        return toResponse(updatedMilestone);
    }

    private void validateDueDate(Project project, java.time.LocalDate dueDate) {
        if (dueDate == null) {
            throw new BadRequestException("Milestone due date is required");
        }
        if (project.getStartDate() != null && dueDate.isBefore(project.getStartDate())) {
            throw new BadRequestException("Milestone due date cannot be before the project start date");
        }
        if (project.getDeadline() != null && dueDate.isAfter(project.getDeadline())) {
            throw new BadRequestException("Milestone due date cannot be after the project deadline");
        }
    }

    @Override
    @Transactional
    public void delete(UUID milestoneId, UUID userId) {
        Milestone milestone = findMilestone(milestoneId);
        Project project = findProject(milestone.getProjectId());
        projectAccess.requireActiveProject(project);
        requireProjectManager(project, userId);
        if (milestone.getDeletedAt() == null) {
            issueRepository.clearMilestoneLink(milestoneId);
            milestone.setDeletedAt(java.time.Instant.now());
            milestoneRepository.save(milestone);
        }
    }

    @Override
    @Transactional
    public MilestoneResponse restore(UUID milestoneId, UUID userId) {
        Milestone milestone = milestoneRepository.findById(milestoneId)
                .orElseThrow(() -> new ResourceNotFoundException("Milestone not found"));
        Project project = findProject(milestone.getProjectId());
        projectAccess.requireActiveProject(project);
        requireProjectManager(project, userId);
        if (milestone.getDeletedAt() != null) {
            milestone.setDeletedAt(null);
            milestoneRepository.save(milestone);
        }
        return toResponse(milestone);
    }

    private MilestoneResponse toResponse(Milestone milestone) {
        List<Issue> linkedIssues = issueRepository.findByMilestoneIdAndDeletedAtIsNull(milestone.getId());
        long totalIssues = linkedIssues.size();
        long completedIssues = linkedIssues.stream()
                .filter(issue -> issue.getStatus() == IssueStatus.CLOSED).count();
        double progress = 0;
        if (totalIssues > 0) {
            progress = completedIssues * 100.0 / totalIssues;
        }
        return MilestoneResponse.fromEntity(milestone, progress);
    }

    private Milestone findMilestone(UUID milestoneId) {
        Milestone milestone = milestoneRepository.findById(milestoneId)
                .orElseThrow(() -> new ResourceNotFoundException("Milestone not found"));
        if (milestone.getDeletedAt() != null) {
            throw new ResourceNotFoundException("Milestone not found");
        }
        return milestone;
    }

    private Project findProject(UUID projectId) {
        return projectRepository.findById(projectId)
                .orElseThrow(() -> new ResourceNotFoundException("Project not found"));
    }

    private void requireProjectManager(Project project, UUID userId) {
        projectAccess.requireWorkspaceAndActive(project, userId);
        if (userId == null || (!userId.equals(project.getAdminId())
                && !userId.equals(project.getProjectManagerId()))) {
            throw new ForbiddenException("Only the project admin or project manager can manage milestones");
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
