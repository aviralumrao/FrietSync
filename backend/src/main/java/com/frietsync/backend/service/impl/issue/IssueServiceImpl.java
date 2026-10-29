package com.frietsync.backend.service.impl.issue;

import com.frietsync.backend.dto.issue.IssueHistoryResponse;
import com.frietsync.backend.dto.issue.ChangeIssueStatusRequest;
import com.frietsync.backend.dto.issue.GitHubIssueAction;
import com.frietsync.backend.dto.issue.IssueCreateV2Request;
import com.frietsync.backend.dto.issue.IssuePageResponse;
import com.frietsync.backend.dto.issue.IssueLabelRequest;
import com.frietsync.backend.dto.issue.IssueRequest;
import com.frietsync.backend.dto.issue.IssueResponse;
import com.frietsync.backend.dto.issue.IssueUpdateRequest;
import com.frietsync.backend.dto.issue.ReviewRequest;
import com.frietsync.backend.dto.project.LabelResponse;
import com.frietsync.backend.entity.issue.Issue;
import com.frietsync.backend.entity.issue.IssueHistoryAction;
import com.frietsync.backend.entity.issue.IssueHistory;
import com.frietsync.backend.entity.issue.IssueLabel;
import com.frietsync.backend.entity.issue.IssuePriority;
import com.frietsync.backend.entity.issue.IssueStatus;
import com.frietsync.backend.entity.notification.NotificationType;
import com.frietsync.backend.entity.project.Label;
import com.frietsync.backend.entity.project.Project;
import com.frietsync.backend.entity.project.Milestone;
import com.frietsync.backend.entity.user.Role;
import com.frietsync.backend.exception.BadRequestException;
import com.frietsync.backend.exception.ForbiddenException;
import com.frietsync.backend.exception.ResourceNotFoundException;
import com.frietsync.backend.repository.issue.IssueAssigneeRepository;
import com.frietsync.backend.repository.issue.IssueAssignmentRequestRepository;
import com.frietsync.backend.repository.issue.IssueHistoryRepository;
import com.frietsync.backend.repository.issue.IssueLabelRepository;
import com.frietsync.backend.repository.issue.IssueRepository;
import com.frietsync.backend.repository.project.LabelRepository;
import com.frietsync.backend.repository.project.ProjectRepository;
import com.frietsync.backend.repository.project.MilestoneRepository;
import com.frietsync.backend.repository.project.ReleaseIssueRepository;
import com.frietsync.backend.repository.user.UserRepository;
import com.frietsync.backend.service.impl.project.ProjectAccess;
import com.frietsync.backend.service.impl.project.MilestoneCompletionService;
import com.frietsync.backend.service.issue.IssueAttachmentService;
import com.frietsync.backend.service.issue.IssueService;
import com.frietsync.backend.service.integration.GitHubIssueLinkService;
import com.frietsync.backend.service.notification.NotificationPublisher;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import com.frietsync.backend.repository.issue.IssueLinkRepository;
import com.frietsync.backend.repository.sprint.SprintRepository;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

@Service
@Transactional
@RequiredArgsConstructor
public class IssueServiceImpl implements IssueService {

    private static final int MAX_PAGE_SIZE = 100;

    private final IssueRepository issueRepository;
    private final IssueAssigneeRepository assigneeRepository;
    private final IssueAssignmentRequestRepository requestRepository;
    private final IssueHistoryRepository historyRepository;
    private final IssueLabelRepository issueLabelRepository;
    private final LabelRepository labelRepository;
    private final ProjectRepository projectRepository;
    private final UserRepository userRepository;
    private final IssueAttachmentService attachmentService;
    private final ProjectAccess access;
    private final IssueMapper mapper;
    private final IssueHistoryRecorder history;
    private final IssueLinkRepository issueLinkRepository;
    private final SprintRepository sprintRepository;
    private final MilestoneRepository milestoneRepository;
    private final MilestoneCompletionService milestoneCompletionService;
    private final ReleaseIssueRepository releaseIssueRepository;
    private final GitHubIssueLinkService githubIssueLinkService;
    private final NotificationPublisher notificationPublisher;

    @Override
    public IssueResponse create(UUID projectId, IssueRequest r, UUID userId) {
        Issue issue = createIssue(projectId, r.getTitle(), r.getDescription(), r.getPriority(), r.getStartDate(),
                r.getDueDate(), r.getEstimatedHours(), r.getMilestoneId(), userId);
        if (r.getGithubIssueNumber() != null) {
            return githubIssueLinkService.link(issue.getId(), r.getGithubIssueNumber(), userId);
        }
        return mapper.toResponse(issue);
    }

    @Override
    public IssueResponse createV2(UUID projectId, IssueCreateV2Request request, UUID userId) {
        GitHubIssueAction action = request.getGithubAction();
        if (action == null) {
            throw new BadRequestException("githubAction is required");
        }
        if (action == GitHubIssueAction.LINK_EXISTING && request.getGithubIssueNumber() == null) {
            throw new BadRequestException("githubIssueNumber is required for LINK_EXISTING");
        }
        if (action != GitHubIssueAction.LINK_EXISTING && request.getGithubIssueNumber() != null) {
            throw new BadRequestException("githubIssueNumber is only allowed for LINK_EXISTING");
        }
        Issue issue = createIssue(projectId, request.getTitle(), request.getDescription(), request.getPriority(),
                request.getStartDate(), request.getDueDate(), request.getEstimatedHours(),
                request.getMilestoneId(), userId);
        return switch (action) {
            case LOCAL -> mapper.toResponse(issue);
            case LINK_EXISTING -> githubIssueLinkService.link(issue.getId(), request.getGithubIssueNumber(), userId);
            case CREATE_NEW -> githubIssueLinkService.createAndLink(issue.getId(), userId);
        };
    }

    private Issue createIssue(UUID projectId, String title, String description, IssuePriority priority,
                              java.time.LocalDate startDate, java.time.LocalDate dueDate,
                              Double estimatedHours, UUID milestoneId, UUID userId) {
        Project project = projectRepository.findByIdForUpdate(projectId)
                .orElseThrow(() -> new ResourceNotFoundException("Project not found"));
        Role role = access.requireMember(project, userId);
        boolean allowed = role == Role.ADMIN || role == Role.PROJECT_MANAGER
                || role == Role.TEAM_LEAD || role == Role.REPORTER;
        if (!allowed) {
            throw new ForbiddenException(
                    "Only the project admin, project manager, team lead, or a reporter can create issues");
        }
        validateIssueDates(project, startDate, dueDate);
        validateMilestone(projectId, milestoneId, startDate, dueDate);
        project.setIssueCounter(project.getIssueCounter() + 1);
        projectRepository.save(project);

        Issue issue = new Issue();
        issue.setProjectId(projectId);
        issue.setIssueNumber(project.getIssueCounter());
        issue.setTitle(title.trim());
        issue.setDescription(description);
        issue.setPriority(priority != null ? priority : IssuePriority.MEDIUM);
        issue.setStartDate(startDate);
        issue.setDueDate(dueDate);
        issue.setMilestoneId(milestoneId);
        issue.setEstimatedHours(estimatedHours);
        issue.setCreatedBy(userId);
        issueRepository.save(issue);
        history.record(issue.getId(), userId, IssueHistoryAction.CREATED);
        return issue;
    }

    @Override
    @Transactional(readOnly = true)
    public IssuePageResponse<IssueResponse> listForProject(UUID projectId, UUID userId, int page, int size) {
        Project project = access.project(projectId);
        access.requireMember(project, userId);
        Page<com.frietsync.backend.entity.issue.Issue> result = issueRepository
                .findByProjectIdAndDeletedAtIsNull(projectId, pageRequest(page, size));
        return IssuePageResponse.<IssueResponse>builder()
                .items(mapper.toResponses(result.getContent()))
                .page(result.getNumber()).size(result.getSize())
                .totalElements(result.getTotalElements()).totalPages(result.getTotalPages()).build();
    }

    @Override
    @Transactional(readOnly = true)
    public List<IssueResponse> listAssignedToMe(UUID userId) {
        UUID workspaceId = access.requireActiveWorkspace(userId);
        return mapper.toResponses(issueRepository.findAssignedToUserInWorkspace(userId, workspaceId));
    }

    @Override
    public IssueResponse get(UUID issueId, UUID userId) {
        Issue issue = access.issue(issueId);
        access.requireMember(access.project(issue.getProjectId()), userId);
        if (issue.getGithubIssueNumber() != null) {
            return githubIssueLinkService.refreshStatus(issueId, userId);
        }
        return mapper.toResponse(issue);
    }

    @Override
    public IssueResponse update(UUID issueId, IssueUpdateRequest r, UUID userId) {
        Issue issue = access.issue(issueId);
        Project project = access.project(issue.getProjectId());
        Role role = access.requireMember(project, userId);
        boolean canEdit = role == Role.ADMIN || role == Role.PROJECT_MANAGER
                || role == Role.TEAM_LEAD
                || (role == Role.REPORTER && userId.equals(issue.getCreatedBy()));
        if (!canEdit) {
            throw new ForbiddenException("Only the project admin, project manager, team lead or issue-creating reporter can edit this issue");
        }
        if (issue.getStatus() == IssueStatus.CLOSED) {
            throw new BadRequestException("Closed issues cannot be edited");
        }

        java.time.LocalDate updatedStartDate = r.getStartDate() == null ? issue.getStartDate() : r.getStartDate();
        java.time.LocalDate updatedDueDate = r.getDueDate() == null ? issue.getDueDate() : r.getDueDate();
        UUID previousMilestoneId = issue.getMilestoneId();
        UUID updatedMilestoneId = r.isUnlinkMilestone() ? null
                : r.getMilestoneId() == null ? previousMilestoneId : r.getMilestoneId();
        if (r.getStartDate() != null || r.getDueDate() != null) {
            validateIssueDates(project, updatedStartDate, updatedDueDate);
            validateLinkedSprintDates(issueId, updatedStartDate, updatedDueDate);
        }
        if (r.getMilestoneId() != null || r.isUnlinkMilestone()
                || r.getStartDate() != null || r.getDueDate() != null) {
            validateMilestone(issue.getProjectId(), updatedMilestoneId, updatedStartDate, updatedDueDate);
        }

        if (r.getTitle() != null) {
            String title = r.getTitle().trim();
            if (title.isEmpty()) {
                throw new BadRequestException("Title cannot be blank");
            }
            track(issueId, userId, "title", issue.getTitle(), title);
            issue.setTitle(title);
        }
        if (r.getDescription() != null) {
            track(issueId, userId, "description", issue.getDescription(), r.getDescription());
            issue.setDescription(r.getDescription());
        }
        if (r.getPriority() != null) {
            track(issueId, userId, "priority", issue.getPriority(), r.getPriority());
            issue.setPriority(r.getPriority());
        }
        if (r.getStartDate() != null) {
            track(issueId, userId, "startDate", issue.getStartDate(), r.getStartDate());
            issue.setStartDate(r.getStartDate());
        }
        if (r.getDueDate() != null) {
            track(issueId, userId, "dueDate", issue.getDueDate(), r.getDueDate());
            issue.setDueDate(r.getDueDate());
        }
        if (!Objects.equals(previousMilestoneId, updatedMilestoneId)) {
            track(issueId, userId, "milestoneId", previousMilestoneId, updatedMilestoneId);
            issue.setMilestoneId(updatedMilestoneId);
        }
        if (r.getEstimatedHours() != null) {
            track(issueId, userId, "estimatedHours", issue.getEstimatedHours(), r.getEstimatedHours());
            issue.setEstimatedHours(r.getEstimatedHours());
        }

        issueRepository.save(issue);
        if (!Objects.equals(previousMilestoneId, updatedMilestoneId)) {
            milestoneCompletionService.refresh(previousMilestoneId, userId);
            milestoneCompletionService.refresh(updatedMilestoneId, userId);
        }
        return mapper.toResponse(issue);
    }

    @Override
    public IssueResponse changeStatus(UUID issueId, ChangeIssueStatusRequest request, UUID userId) {
        Issue issue = access.issue(issueId);
        Project project = access.project(issue.getProjectId());
        Role role = access.requireMember(project, userId);
        if (role != Role.ADMIN && role != Role.PROJECT_MANAGER) {
            throw new ForbiddenException("Only the project admin or project manager can change issue status");
        }

        IssueStatus previousStatus = issue.getStatus();
        IssueStatus newStatus = request.status();
        if (previousStatus == newStatus) {
            return mapper.toResponse(issue);
        }

        Instant now = Instant.now();
        issue.setStatus(newStatus);
        if (newStatus == IssueStatus.CLOSED) {
            if (issue.getResolvedAt() == null) issue.setResolvedAt(now);
            issue.setClosedAt(now);
        } else if (newStatus == IssueStatus.RESOLVED) {
            issue.setResolvedAt(now);
            issue.setClosedAt(null);
        } else {
            issue.setResolvedAt(null);
            issue.setClosedAt(null);
        }

        issueRepository.save(issue);
        milestoneCompletionService.refresh(issue.getMilestoneId(), userId);
        if (newStatus == IssueStatus.RESOLVED || newStatus == IssueStatus.CLOSED) {
            requestRepository.deleteByIssueId(issueId);
        }
        history.record(issueId, userId, IssueHistoryAction.UPDATED,
                "status", previousStatus, newStatus);
        return mapper.toResponse(issue);
    }

    @Override
    public void delete(UUID issueId, UUID userId) {
        Issue issue = access.issue(issueId);
        Project project = access.project(issue.getProjectId());
        Role role = access.requireMember(project, userId);

        boolean allowed = role == Role.ADMIN
                || role == Role.PROJECT_MANAGER
                || role == Role.TEAM_LEAD
                || (role == Role.REPORTER && userId.equals(issue.getCreatedBy()));
        if (!allowed) {
            throw new ForbiddenException("You don't have permission to delete this issue");
        }
        if (issue.getDeletedAt() == null) {
            attachmentService.deleteAllForIssue(issue.getId());
            issueLabelRepository.deleteByIssueId(issue.getId());
            assigneeRepository.deleteByIssueId(issue.getId());
            requestRepository.deleteByIssueId(issue.getId());
            releaseIssueRepository.deleteByIssueId(issue.getId());
            issueLinkRepository.deleteBySourceIssueIdOrTargetIssueId(issue.getId(), issue.getId());
            sprintRepository.clearIssueLink(issue.getId());
            issue.setDeletedAt(Instant.now());
            issueRepository.save(issue);
            history.record(issue.getId(), userId, IssueHistoryAction.DELETED);
            milestoneCompletionService.refresh(issue.getMilestoneId(), userId);
        }
    }

    @Override
    public IssueResponse resolve(UUID issueId, UUID userId) {
        Issue issue = access.issue(issueId);
        Project project = access.project(issue.getProjectId());
        Role role = access.requireMember(project, userId);

        boolean assignee = assigneeRepository.existsByIssueIdAndUserId(issueId, userId);
        boolean allowed = role == Role.CONTRIBUTOR && assignee;
        if (!allowed) {
            throw new ForbiddenException("Only an assigned contributor can resolve this issue");
        }
        if (issue.getStatus() == IssueStatus.RESOLVED || issue.getStatus() == IssueStatus.CLOSED) {
            throw new BadRequestException("This issue is already resolved or closed");
        }

        IssueStatus old = issue.getStatus();
        issue.setStatus(IssueStatus.RESOLVED);
        issue.setResolvedAt(Instant.now());
        issueRepository.save(issue);
        milestoneCompletionService.refresh(issue.getMilestoneId(), userId);
        requestRepository.deleteByIssueId(issueId);

        history.record(issueId, userId, IssueHistoryAction.RESOLVED, "status", old, issue.getStatus());
        notifyIssuePeople(issue, project, NotificationType.ISSUE_RESOLVED,
                "Issue resolved", "Issue #" + issue.getIssueNumber() + ": " + issue.getTitle()
                        + " was resolved.", userId);
        return mapper.toResponse(issue);
    }

    @Override
    public IssueResponse approveReview(UUID issueId, ReviewRequest request, UUID userId) {
        return review(issueId, request, userId, true);
    }

    @Override
    public IssueResponse rejectReview(UUID issueId, ReviewRequest request, UUID userId) {
        return review(issueId, request, userId, false);
    }

    private IssueResponse review(UUID issueId, ReviewRequest request, UUID userId, boolean approve) {
        Issue issue = access.issue(issueId);
        Project project = access.project(issue.getProjectId());
        Role role = access.requireMember(project, userId);
        if (role != Role.ADMIN && role != Role.PROJECT_MANAGER && role != Role.TEAM_LEAD
                && role != Role.REPORTER) {
            throw new ForbiddenException("Only the project admin, project manager, team lead or a reporter can review this issue");
        }
        if (issue.getStatus() != IssueStatus.RESOLVED) {
            throw new BadRequestException("Only resolved issues can be reviewed");
        }

        IssueStatus old = issue.getStatus();
        if (approve) {
            issue.setStatus(IssueStatus.CLOSED);
            issue.setClosedAt(Instant.now());
        } else {
            issue.setStatus(IssueStatus.REOPENED);
            issue.setResolvedAt(null);
        }
        issueRepository.save(issue);
        milestoneCompletionService.refresh(issue.getMilestoneId(), userId);
        if (approve) {
            requestRepository.deleteByIssueId(issueId);
        }
        githubIssueLinkService.syncStatus(issue, userId);

        history.record(issueId, userId,
                approve ? IssueHistoryAction.REVIEW_APPROVED : IssueHistoryAction.REVIEW_REJECTED,
                "status", old, issue.getStatus(),
                request == null ? null : request.getComment());
        notifyIssuePeople(issue, project,
                approve ? NotificationType.ISSUE_REVIEW_APPROVED : NotificationType.ISSUE_REVIEW_REJECTED,
                approve ? "Issue review approved" : "Issue review rejected",
                "Review of issue #" + issue.getIssueNumber() + ": " + issue.getTitle()
                        + (approve ? " was approved." : " was rejected and the issue reopened."), userId);
        return mapper.toResponse(issue);
    }

    private void notifyIssuePeople(Issue issue, Project project, NotificationType type,
                                   String title, String message, UUID actorId) {
        java.util.Set<UUID> recipients = new java.util.LinkedHashSet<>();
        if (issue.getCreatedBy() != null) recipients.add(issue.getCreatedBy());
        assigneeRepository.findByIssueId(issue.getId()).stream()
                .map(com.frietsync.backend.entity.issue.IssueAssignee::getUserId).forEach(recipients::add);
        if (project.getAdminId() != null) recipients.add(project.getAdminId());
        if (project.getProjectManagerId() != null) recipients.add(project.getProjectManagerId());
        if (project.getTeamLeadId() != null) recipients.add(project.getTeamLeadId());
        recipients.forEach(recipient -> notificationPublisher.publish(recipient, type, title, message,
                project.getId(), issue.getId(), actorId));
    }

    @Override
    @Transactional(readOnly = true)
    public IssuePageResponse<IssueHistoryResponse> history(UUID issueId, UUID userId, int page, int size) {
        com.frietsync.backend.entity.issue.Issue issue = issueRepository.findById(issueId)
                .orElseThrow(() -> new ResourceNotFoundException("Issue not found"));
        access.requireMember(access.project(issue.getProjectId()), userId);
        Page<IssueHistory> historyPage = historyRepository
                .findByIssueIdOrderByCreatedAtDescIdDesc(issueId, pageRequest(page, size));
        Map<UUID, String> actorNames = new java.util.HashMap<>();
        List<UUID> actorIds = historyPage.getContent().stream()
                .map(IssueHistory::getActorId)
                .filter(Objects::nonNull).distinct().toList();
        userRepository.findAllById(actorIds).forEach(actor -> actorNames.put(actor.getId(), actor.getName()));
        Page<IssueHistoryResponse> result = historyPage.map(h -> IssueHistoryResponse.builder()
                        .id(h.getId())
                        .actorId(h.getActorId())
                        .actorName(actorNames.get(h.getActorId()))
                        .action(h.getAction())
                        .field(h.getFieldName())
                        .oldValue(h.getOldValue())
                        .newValue(h.getNewValue())
                        .comment(h.getComment())
                        .createdAt(h.getCreatedAt())
                        .build());
        return IssuePageResponse.<IssueHistoryResponse>builder()
                .items(result.getContent()).page(result.getNumber()).size(result.getSize())
                .totalElements(result.getTotalElements()).totalPages(result.getTotalPages()).build();
    }

    private PageRequest pageRequest(int page, int size) {
        return PageRequest.of(Math.max(0, page), Math.min(Math.max(1, size), MAX_PAGE_SIZE),
                Sort.by(Sort.Direction.DESC, "createdAt").and(Sort.by(Sort.Direction.DESC, "id")));
    }

    @Override
    public void removeLabel(UUID issueId, UUID labelId, UUID userId) {
        Issue issue = access.issue(issueId);
        Project project = access.project(issue.getProjectId());
        access.require(project, userId, Role.PROJECT_MANAGER, Role.TEAM_LEAD);
        Label label = labelRepository.findById(labelId)
                .orElseThrow(() -> new ResourceNotFoundException("Label not found"));
        if (!label.getProjectId().equals(issue.getProjectId())) {
            throw new BadRequestException("Label does not belong to this project");
        }
        if (!issueLabelRepository.existsByIssueIdAndLabelId(issueId, labelId)) {
            throw new ResourceNotFoundException("Label is not attached to this issue");
        }
        issueLabelRepository.deleteByIssueIdAndLabelId(issueId, labelId);
        history.record(issueId, userId, IssueHistoryAction.LABEL_REMOVED, "label", label.getName(), null);
    }

    @Override
    public LabelResponse addLabel(UUID issueId, IssueLabelRequest request, UUID userId) {
        Issue issue = access.issue(issueId);
        Project project = access.project(issue.getProjectId());
        access.require(project, userId, Role.PROJECT_MANAGER, Role.TEAM_LEAD);

        String name = request.getName().trim();
        Label label = labelRepository.findByProjectIdAndNameIgnoreCase(issue.getProjectId(), name)
                .orElseGet(() -> {
                    Label newLabel = new Label();
                    newLabel.setProjectId(issue.getProjectId());
                    newLabel.setName(name);
                    return labelRepository.save(newLabel);
                });
        if (issueLabelRepository.existsByIssueIdAndLabelId(issueId, label.getId())) {
            throw new BadRequestException("Label is already on this issue");
        }

        IssueLabel link = new IssueLabel();
        link.setIssueId(issueId);
        link.setLabelId(label.getId());
        issueLabelRepository.save(link);

        history.record(issueId, userId, IssueHistoryAction.LABEL_ADDED, "label", null, label.getName());
        return toLabelResponse(label);
    }

    @Override
    @Transactional(readOnly = true)
    public List<LabelResponse> labels(UUID issueId, UUID userId) {
        Issue issue = access.issue(issueId);
        access.requireMember(access.project(issue.getProjectId()), userId);
        List<UUID> labelIds = issueLabelRepository.findByIssueId(issueId).stream()
                .map(IssueLabel::getLabelId)
                .toList();
        return labelRepository.findAllById(labelIds).stream()
                .map(this::toLabelResponse)
                .toList();
    }

    @Override
    public void deleteAllForProject(UUID projectId) {
        issueRepository.findByProjectId(projectId).forEach(this::purge);
    }

    private void purge(Issue issue) {
        UUID id = issue.getId();
        attachmentService.deleteAllForIssue(id);
        issueLabelRepository.deleteByIssueId(id);
        assigneeRepository.deleteByIssueId(id);
        requestRepository.deleteByIssueId(id);
        releaseIssueRepository.deleteByIssueId(id);
        issueLinkRepository.deleteBySourceIssueIdOrTargetIssueId(id, id);
        sprintRepository.clearIssueLink(id);
        historyRepository.deleteByIssueId(id);
        issueRepository.delete(issue);
    }

    private void validateIssueDates(Project project, java.time.LocalDate startDate, java.time.LocalDate dueDate) {
        if (startDate != null && dueDate != null && dueDate.isBefore(startDate)) {
            throw new BadRequestException("Issue due date cannot be before its start date");
        }
        if (project.getStartDate() != null) {
            if (startDate != null && startDate.isBefore(project.getStartDate())) {
                throw new BadRequestException("Issue start date cannot be before the project start date");
            }
            if (dueDate != null && dueDate.isBefore(project.getStartDate())) {
                throw new BadRequestException("Issue due date cannot be before the project start date");
            }
        }
        if (project.getDeadline() != null) {
            if (startDate != null && startDate.isAfter(project.getDeadline())) {
                throw new BadRequestException("Issue start date cannot be after the project deadline");
            }
            if (dueDate != null && dueDate.isAfter(project.getDeadline())) {
                throw new BadRequestException("Issue due date cannot be after the project deadline");
            }
        }
    }

    private void validateLinkedSprintDates(UUID issueId, java.time.LocalDate startDate, java.time.LocalDate dueDate) {
        for (var task : sprintRepository.findByIssueId(issueId)) {
            if (startDate != null && task.getStartDate() != null && task.getStartDate().isBefore(startDate)) {
                throw new BadRequestException("Issue start date cannot be after a linked task start date");
            }
            if (dueDate != null && task.getEndDate() != null && task.getEndDate().isAfter(dueDate)) {
                throw new BadRequestException("Issue due date cannot be before a linked task end date");
            }
        }
    }

    private void validateMilestone(UUID projectId, UUID milestoneId, java.time.LocalDate startDate,
                                   java.time.LocalDate dueDate) {
        if (milestoneId == null) return;
        Milestone milestone = milestoneRepository.findById(milestoneId)
                .orElseThrow(() -> new ResourceNotFoundException("Milestone not found"));
        if (!projectId.equals(milestone.getProjectId())) {
            throw new BadRequestException("Milestone must belong to the issue's project");
        }
        if (startDate != null && startDate.isAfter(milestone.getDueDate())) {
            throw new BadRequestException("Issue start date cannot be after the milestone due date");
        }
        if (dueDate != null && dueDate.isAfter(milestone.getDueDate())) {
            throw new BadRequestException("Issue due date cannot be after the milestone due date");
        }
    }

    private void track(UUID issueId, UUID userId, String field, Object oldValue, Object newValue) {
        if (!Objects.equals(oldValue, newValue)) {
            history.record(issueId, userId, IssueHistoryAction.UPDATED, field, oldValue, newValue);
        }
    }

    private LabelResponse toLabelResponse(Label label) {
        LabelResponse response = new LabelResponse();
        response.setId(label.getId());
        response.setName(label.getName());
        response.setColor(label.getColor());
        return response;
    }
}
