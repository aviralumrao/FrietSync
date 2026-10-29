package com.frietsync.backend.service.impl.issue;

import com.frietsync.backend.dto.issue.AssignIssueRequest;
import com.frietsync.backend.dto.issue.AssignmentDecisionRequest;
import com.frietsync.backend.dto.issue.AssignmentRequestResponse;
import com.frietsync.backend.dto.issue.IssueResponse;
import com.frietsync.backend.entity.issue.AssignmentRequestStatus;
import com.frietsync.backend.entity.issue.Issue;
import com.frietsync.backend.entity.issue.IssueAssignee;
import com.frietsync.backend.entity.issue.IssueAssignmentRequest;
import com.frietsync.backend.entity.issue.IssueHistoryAction;
import com.frietsync.backend.entity.issue.IssueStatus;
import com.frietsync.backend.entity.notification.NotificationType;
import com.frietsync.backend.entity.project.Project;
import com.frietsync.backend.entity.project.ProjectMember;
import com.frietsync.backend.entity.user.Role;
import com.frietsync.backend.entity.user.User;
import com.frietsync.backend.exception.BadRequestException;
import com.frietsync.backend.exception.ForbiddenException;
import com.frietsync.backend.exception.ResourceNotFoundException;
import com.frietsync.backend.repository.issue.IssueAssigneeRepository;
import com.frietsync.backend.repository.issue.IssueAssignmentRequestRepository;
import com.frietsync.backend.repository.issue.IssueRepository;
import com.frietsync.backend.repository.project.ProjectMemberRepository;
import com.frietsync.backend.repository.user.UserRepository;
import com.frietsync.backend.service.impl.project.ProjectAccess;
import com.frietsync.backend.service.issue.IssueAssignmentService;
import com.frietsync.backend.service.notification.NotificationPublisher;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@Transactional
@RequiredArgsConstructor
public class IssueAssignmentServiceImpl implements IssueAssignmentService {

    private final IssueRepository issueRepository;
    private final IssueAssigneeRepository assigneeRepository;
    private final IssueAssignmentRequestRepository requestRepository;
    private final ProjectMemberRepository memberRepository;
    private final UserRepository userRepository;
    private final ProjectAccess access;
    private final IssueMapper mapper;
    private final IssueHistoryRecorder history;
    private final NotificationPublisher notificationPublisher;

    @Override
    public IssueResponse assign(UUID issueId, AssignIssueRequest r, UUID userId) {
        Issue issue = issueRepository.findByIdForUpdate(issueId)
                .orElseThrow(() -> new ResourceNotFoundException("Issue not found"));
        Project project = access.project(issue.getProjectId());
        access.require(project, userId, Role.PROJECT_MANAGER, Role.TEAM_LEAD);
        ensureAssignable(issue);

        if (r.getUserIds() == null) {
            throw new BadRequestException("userIds is required; use an empty list to unassign everyone");
        }
        Set<UUID> target = new LinkedHashSet<>(r.getUserIds());
        if (target.size() != r.getUserIds().size()) {
            throw new BadRequestException("Assignee IDs must be unique");
        }
        if (target.size() > 10) {
            throw new BadRequestException("An issue can have at most 10 assignees");
        }
        for (UUID uid : target) {
            access.requireWorkspaceAndActive(project, uid);
            if (access.roleOf(project, uid) != Role.CONTRIBUTOR) {
                throw new BadRequestException("Only project contributors can be assigned to issues");
            }
        }

        List<IssueAssignee> rows = assigneeRepository.findByIssueId(issueId);
        Set<UUID> current = rows.stream()
                .map(IssueAssignee::getUserId)
                .collect(Collectors.toCollection(LinkedHashSet::new));

        assigneeRepository.deleteAll(rows.stream()
                .filter(a -> !target.contains(a.getUserId()))
                .toList());
        for (UUID uid : target) {
            if (!current.contains(uid)) {
                addAssignee(issueId, uid, userId);
                notificationPublisher.publish(uid, NotificationType.ISSUE_ASSIGNED,
                        "Issue assigned to you", "You were assigned to issue #" + issue.getIssueNumber()
                                + ": " + issue.getTitle(), project.getId(), issueId, userId);
            }
        }

        for (UUID uid : target) {
            requestRepository.findByIssueIdAndUserId(issueId, uid)
                    .filter(req -> req.getStatus() == AssignmentRequestStatus.PENDING)
                    .ifPresent(req -> decideRequest(req, true, userId));
        }

        IssueStatus oldStatus = issue.getStatus();
        if (!target.isEmpty()) {
            startWork(issue);
        }
        if (oldStatus != issue.getStatus()) {
            history.record(issueId, userId, IssueHistoryAction.UPDATED, "status", oldStatus, issue.getStatus());
        }
        history.record(issueId, userId, IssueHistoryAction.ASSIGNED, "assignees", current, target);
        return mapper.toResponse(issue);
    }

    @Override
    public AssignmentRequestResponse requestAssignment(UUID issueId, UUID userId) {
        Issue issue = access.issue(issueId);
        Project project = access.project(issue.getProjectId());
        Role role = access.requireMember(project, userId);
        if (role != Role.CONTRIBUTOR) {
            throw new ForbiddenException("Only contributors can request an issue");
        }
        ensureAssignable(issue);
        if (assigneeRepository.existsByIssueIdAndUserId(issueId, userId)) {
            throw new BadRequestException("You are already assigned to this issue");
        }
        if (assigneeRepository.findByIssueId(issueId).size() >= 10) {
            throw new BadRequestException("This issue already has the maximum of 10 assignees");
        }

        IssueAssignmentRequest req = requestRepository.findByIssueIdAndUserId(issueId, userId).orElse(null);
        if (req != null && req.getStatus() == AssignmentRequestStatus.PENDING) {
            throw new BadRequestException("You already have a pending request for this issue");
        }
        if (req == null) {
            req = new IssueAssignmentRequest();
            req.setProjectId(issue.getProjectId());
            req.setIssueId(issueId);
            req.setUserId(userId);
        }
        req.setStatus(AssignmentRequestStatus.PENDING);
        req.setDecidedBy(null);
        req.setDecidedAt(null);
        requestRepository.save(req);

        history.record(issueId, userId, IssueHistoryAction.ASSIGNMENT_REQUESTED);
        notifyProjectLeads(project, NotificationType.ISSUE_ASSIGNMENT_REQUESTED,
                "Issue assignment requested", "A contributor requested issue #" + issue.getIssueNumber()
                        + ": " + issue.getTitle(), issueId, userId);
        return toResponse(req);
    }

    @Override
    @Transactional(readOnly = true)
    public AssignmentRequestResponse myRequest(UUID issueId, UUID userId) {
        Issue issue = access.issue(issueId);
        access.requireMember(access.project(issue.getProjectId()), userId);
        IssueAssignmentRequest req = requestRepository.findByIssueIdAndUserId(issueId, userId)
                .orElseThrow(() -> new ResourceNotFoundException("You have not made a request for this issue"));
        return toResponse(req);
    }

    @Override
    public void cancelMyRequest(UUID issueId, UUID userId) {
        Issue issue = access.issue(issueId);
        access.requireMember(access.project(issue.getProjectId()), userId);
        IssueAssignmentRequest req = requestRepository.findByIssueIdAndUserId(issueId, userId)
                .orElseThrow(() -> new ResourceNotFoundException("You have not made a request for this issue"));
        if (req.getStatus() != AssignmentRequestStatus.PENDING) {
            throw new BadRequestException("Only pending requests can be deleted");
        }
        requestRepository.delete(req);
    }

    @Override
    @Transactional(readOnly = true)
    public List<AssignmentRequestResponse> listRequests(UUID projectId, AssignmentRequestStatus status, UUID userId) {
        Project project = access.project(projectId);
        access.require(project, userId, Role.PROJECT_MANAGER, Role.TEAM_LEAD);
        AssignmentRequestStatus filter = status != null ? status : AssignmentRequestStatus.PENDING;
        return toResponses(requestRepository.findByProjectIdAndStatusOrderByCreatedAtAsc(projectId, filter));
    }

    @Override
    public AssignmentRequestResponse decide(UUID issueId, AssignmentDecisionRequest d, UUID userId) {
        Issue issue = issueRepository.findByIdForUpdate(issueId)
                .orElseThrow(() -> new ResourceNotFoundException("Issue not found"));
        Project project = access.project(issue.getProjectId());
        access.require(project, userId, Role.PROJECT_MANAGER, Role.TEAM_LEAD);

        IssueAssignmentRequest req = requestRepository.findById(d.getRequestId())
                .orElseThrow(() -> new ResourceNotFoundException("Request not found"));
        if (!req.getIssueId().equals(issueId)) {
            throw new BadRequestException("Request does not belong to this issue");
        }
        if (req.getStatus() != AssignmentRequestStatus.PENDING) {
            throw new BadRequestException("This request has already been decided");
        }

        boolean approve = Boolean.TRUE.equals(d.getApprove());
        if (approve) {
            ensureAssignable(issue);
            access.requireWorkspaceAndActive(project, req.getUserId());
            if (access.roleOf(project, req.getUserId()) != Role.CONTRIBUTOR) {
                throw new BadRequestException("Only project contributors can be assigned to issues");
            }
            if (!assigneeRepository.existsByIssueIdAndUserId(issueId, req.getUserId())) {
                if (assigneeRepository.findByIssueId(issueId).size() >= 10) {
                    throw new BadRequestException("This issue already has the maximum of 10 assignees");
                }
                addAssignee(issueId, req.getUserId(), userId);
                notificationPublisher.publish(req.getUserId(), NotificationType.ISSUE_ASSIGNED,
                        "Issue assigned to you", "You were assigned to issue #" + issue.getIssueNumber()
                                + ": " + issue.getTitle(), project.getId(), issueId, userId);
            }
            IssueStatus oldStatus = issue.getStatus();
            startWork(issue);
            if (oldStatus != issue.getStatus()) {
                history.record(issueId, userId, IssueHistoryAction.UPDATED, "status", oldStatus, issue.getStatus());
            }
        }
        decideRequest(req, approve, userId);
        return toResponse(req);
    }

    @Override
    public AssignmentRequestResponse rejectRequest(UUID projectId, UUID requestId, UUID userId) {
        Project project = access.project(projectId);
        access.require(project, userId, Role.PROJECT_MANAGER, Role.TEAM_LEAD);

        IssueAssignmentRequest req = requestRepository.findById(requestId)
                .orElseThrow(() -> new ResourceNotFoundException("Request not found"));
        if (!req.getProjectId().equals(projectId)) {
            throw new BadRequestException("Request does not belong to this project");
        }
        if (req.getStatus() != AssignmentRequestStatus.PENDING) {
            throw new BadRequestException("This request has already been decided");
        }
        decideRequest(req, false, userId);
        return toResponse(req);
    }

    private void ensureAssignable(Issue issue) {
        if (issue.getStatus() == IssueStatus.RESOLVED || issue.getStatus() == IssueStatus.CLOSED) {
            throw new BadRequestException("Resolved or closed issues cannot be assigned");
        }
    }

    private void startWork(Issue issue) {
        if (issue.getStatus() == IssueStatus.OPEN || issue.getStatus() == IssueStatus.REOPENED) {
            issue.setStatus(IssueStatus.IN_PROGRESS);
            issueRepository.save(issue);
        }
    }

    private void addAssignee(UUID issueId, UUID assigneeId, UUID assignedBy) {
        IssueAssignee a = new IssueAssignee();
        a.setIssueId(issueId);
        a.setUserId(assigneeId);
        a.setAssignedBy(assignedBy);
        assigneeRepository.save(a);
    }

    private void decideRequest(IssueAssignmentRequest req, boolean approve, UUID deciderId) {
        req.setStatus(approve ? AssignmentRequestStatus.APPROVED : AssignmentRequestStatus.REJECTED);
        req.setDecidedBy(deciderId);
        req.setDecidedAt(Instant.now());
        requestRepository.save(req);
        history.record(req.getIssueId(), deciderId,
                approve ? IssueHistoryAction.ASSIGNMENT_APPROVED : IssueHistoryAction.ASSIGNMENT_REJECTED,
                "assignee", null, req.getUserId());
        Issue issue = issueRepository.findById(req.getIssueId()).orElse(null);
        String issueLabel = issue == null ? "this issue"
                : "issue #" + issue.getIssueNumber() + ": " + issue.getTitle();
        notificationPublisher.publish(req.getUserId(), approve
                        ? NotificationType.ISSUE_REQUEST_APPROVED : NotificationType.ISSUE_REQUEST_REJECTED,
                approve ? "Issue request approved" : "Issue request rejected",
                "Your request for " + issueLabel + " was " + (approve ? "approved" : "rejected") + ".",
                req.getProjectId(), req.getIssueId(), deciderId);
        requestRepository.delete(req);
    }

    private void notifyProjectLeads(Project project, NotificationType type, String title,
                                    String message, UUID issueId, UUID actorId) {
        Set<UUID> recipients = new LinkedHashSet<>();
        if (project.getAdminId() != null) recipients.add(project.getAdminId());
        if (project.getProjectManagerId() != null) recipients.add(project.getProjectManagerId());
        if (project.getTeamLeadId() != null) recipients.add(project.getTeamLeadId());
        for (ProjectMember member : memberRepository.findByProjectId(project.getId())) {
            if (member.getRole() == Role.ADMIN || member.getRole() == Role.PROJECT_MANAGER
                    || member.getRole() == Role.TEAM_LEAD) {
                recipients.add(member.getUserId());
            }
        }
        recipients.forEach(recipient -> notificationPublisher.publish(recipient, type, title, message,
                project.getId(), issueId, actorId));
    }

    private AssignmentRequestResponse toResponse(IssueAssignmentRequest req) {
        Map<UUID, User> users = new HashMap<>();
        userRepository.findAllById(java.util.stream.Stream.of(req.getUserId(), req.getDecidedBy())
                .filter(java.util.Objects::nonNull).toList()).forEach(user -> users.put(user.getId(), user));
        return build(req, users);
    }

    private List<AssignmentRequestResponse> toResponses(List<IssueAssignmentRequest> requests) {
        Map<UUID, User> users = new HashMap<>();
        List<UUID> ids = requests.stream()
                .flatMap(request -> java.util.stream.Stream.of(request.getUserId(), request.getDecidedBy()))
                .filter(java.util.Objects::nonNull).distinct().toList();
        userRepository.findAllById(ids)
                .forEach(u -> users.put(u.getId(), u));
        return requests.stream().map(r -> build(r, users)).toList();
    }

    private AssignmentRequestResponse build(IssueAssignmentRequest r, Map<UUID, User> users) {
        User requester = users.get(r.getUserId());
        User decider = users.get(r.getDecidedBy());
        return AssignmentRequestResponse.builder()
                .id(r.getId())
                .projectId(r.getProjectId())
                .issueId(r.getIssueId())
                .userId(r.getUserId())
                .requesterName(requester == null ? null : requester.getName())
                .requesterEmail(requester == null ? null : requester.getEmail())
                .status(r.getStatus())
                .decidedBy(r.getDecidedBy())
                .decidedByName(decider == null ? null : decider.getName())
                .decidedAt(r.getDecidedAt())
                .createdAt(r.getCreatedAt())
                .build();
    }
}
