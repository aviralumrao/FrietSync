package com.frietsync.backend.service.impl.issue;

import com.frietsync.backend.dto.issue.AssignIssueRequest;
import com.frietsync.backend.dto.issue.AssignmentDecisionRequest;
import com.frietsync.backend.dto.issue.AssignmentRequestBody;
import com.frietsync.backend.dto.issue.AssignmentRequestResponse;
import com.frietsync.backend.dto.issue.IssueResponse;
import com.frietsync.backend.entity.issue.AssignmentRequestStatus;
import com.frietsync.backend.entity.issue.Issue;
import com.frietsync.backend.entity.issue.IssueAssignee;
import com.frietsync.backend.entity.issue.IssueAssignmentRequest;
import com.frietsync.backend.entity.issue.IssueHistoryAction;
import com.frietsync.backend.entity.issue.IssueStatus;
import com.frietsync.backend.entity.project.Project;
import com.frietsync.backend.entity.user.Role;
import com.frietsync.backend.entity.user.User;
import com.frietsync.backend.exception.BadRequestException;
import com.frietsync.backend.exception.ForbiddenException;
import com.frietsync.backend.exception.ResourceNotFoundException;
import com.frietsync.backend.repository.issue.IssueAssigneeRepository;
import com.frietsync.backend.repository.issue.IssueAssignmentRequestRepository;
import com.frietsync.backend.repository.issue.IssueRepository;
import com.frietsync.backend.repository.user.UserRepository;
import com.frietsync.backend.service.impl.project.ProjectAccess;
import com.frietsync.backend.service.issue.IssueAssignmentService;
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
    private final UserRepository userRepository;
    private final ProjectAccess access;
    private final IssueMapper mapper;
    private final IssueHistoryRecorder history;

    @Override
    public IssueResponse assign(UUID issueId, AssignIssueRequest r, UUID userId) {
        Issue issue = access.issue(issueId);
        Project project = access.project(issue.getProjectId());
        access.require(project, userId, Role.PROJECT_MANAGER, Role.TEAM_LEAD);
        ensureAssignable(issue);

        Set<UUID> target = new LinkedHashSet<>(r.getUserIds());
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
    public AssignmentRequestResponse requestAssignment(UUID issueId, AssignmentRequestBody body, UUID userId) {
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
        req.setMessage(body == null ? null : body.getMessage());
        req.setDecidedBy(null);
        req.setDecidedAt(null);
        requestRepository.save(req);

        history.record(issueId, userId, IssueHistoryAction.ASSIGNMENT_REQUESTED);
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
        Issue issue = access.issue(issueId);
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
                addAssignee(issueId, req.getUserId(), userId);
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
    }

    private AssignmentRequestResponse toResponse(IssueAssignmentRequest req) {
        return build(req, userRepository.findById(req.getUserId()).orElse(null));
    }

    private List<AssignmentRequestResponse> toResponses(List<IssueAssignmentRequest> requests) {
        Map<UUID, User> users = new HashMap<>();
        userRepository.findAllById(requests.stream().map(IssueAssignmentRequest::getUserId).distinct().toList())
                .forEach(u -> users.put(u.getId(), u));
        return requests.stream().map(r -> build(r, users.get(r.getUserId()))).toList();
    }

    private AssignmentRequestResponse build(IssueAssignmentRequest r, User requester) {
        return AssignmentRequestResponse.builder()
                .id(r.getId())
                .projectId(r.getProjectId())
                .issueId(r.getIssueId())
                .userId(r.getUserId())
                .requesterName(requester == null ? null : requester.getName())
                .requesterEmail(requester == null ? null : requester.getEmail())
                .status(r.getStatus())
                .message(r.getMessage())
                .decidedBy(r.getDecidedBy())
                .decidedAt(r.getDecidedAt())
                .createdAt(r.getCreatedAt())
                .build();
    }
}
