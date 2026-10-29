package com.frietsync.backend.service.impl.sprint;

import com.frietsync.backend.dto.sprint.CreateSprintRequest;
import com.frietsync.backend.dto.sprint.ChangeSprintStatusRequest;
import com.frietsync.backend.dto.sprint.SprintAssignmentRequestResponse;
import com.frietsync.backend.dto.sprint.SprintResponse;
import com.frietsync.backend.dto.sprint.UpdateSprintRequest;
import com.frietsync.backend.entity.project.Project;
import com.frietsync.backend.entity.issue.Issue;
import com.frietsync.backend.entity.sprint.Sprint;
import com.frietsync.backend.entity.sprint.SprintAssignmentRequest;
import com.frietsync.backend.entity.sprint.SprintAssignmentRequestStatus;
import com.frietsync.backend.entity.sprint.SprintStatus;
import com.frietsync.backend.entity.project.ProjectMember;
import com.frietsync.backend.entity.notification.NotificationType;
import com.frietsync.backend.entity.user.Role;
import com.frietsync.backend.entity.user.User;
import com.frietsync.backend.exception.BadRequestException;
import com.frietsync.backend.exception.ForbiddenException;
import com.frietsync.backend.exception.ResourceNotFoundException;
import com.frietsync.backend.repository.project.ProjectRepository;
import com.frietsync.backend.repository.project.ProjectMemberRepository;
import com.frietsync.backend.repository.issue.IssueRepository;
import com.frietsync.backend.repository.sprint.SprintAssignmentRequestRepository;
import com.frietsync.backend.repository.sprint.SprintRepository;
import com.frietsync.backend.repository.sprint.SprintCommentRepository;
import com.frietsync.backend.repository.user.UserRepository;
import com.frietsync.backend.service.sprint.SprintService;
import com.frietsync.backend.service.notification.NotificationPublisher;
import com.frietsync.backend.service.impl.project.ProjectAccess;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class SprintServiceImpl implements SprintService {

    private final SprintRepository sprintRepository;
    private final ProjectRepository projectRepository;
    private final ProjectMemberRepository projectMemberRepository;
    private final IssueRepository issueRepository;
    private final UserRepository userRepository;
    private final SprintAssignmentRequestRepository assignmentRequestRepository;
    private final SprintCommentRepository sprintCommentRepository;
    private final ProjectAccess projectAccess;
    private final NotificationPublisher notificationPublisher;

    @Override
    @Transactional
    public SprintResponse createSprint(UUID projectId, CreateSprintRequest request, UUID currentUserId) {
        Project project = projectRepository.findById(projectId)
                .orElseThrow(() -> new ResourceNotFoundException("Project not found"));

        requireProjectManagerOrTeamLeadorAdmin(project, currentUserId);
        checkIssueProject(projectId, request.getIssueId(), request.getStartDate(), request.getEndDate());
        checkDates(request.getStartDate(), request.getEndDate());
        checkDatesWithinProject(project, request.getStartDate(), request.getEndDate());
        Integer highestNumber = sprintRepository.findMaxSprintNumber(projectId);
        int nextNumber = Math.max(project.getSprintCounter(), highestNumber == null ? 0 : highestNumber) + 1;
        Sprint sprint = new Sprint();
        sprint.setProjectId(projectId);
        sprint.setIssueId(request.getIssueId());
        sprint.setSprintNumber(nextNumber);
        sprint.setName(request.getName());
        sprint.setGoal(request.getGoal());
        sprint.setStatus(SprintStatus.PLANNED);
        sprint.setStartDate(request.getStartDate());
        sprint.setEndDate(request.getEndDate());
        sprint.setCreatedBy(currentUserId);

        Sprint savedSprint = sprintRepository.save(sprint);
        project.setSprintCounter(nextNumber);
        projectRepository.save(project);
        return toResponse(savedSprint);
    }

    @Override
    @Transactional(readOnly = true)
    public List<SprintResponse> listSprints(UUID projectId, UUID currentUserId) {
        checkMember(projectId, currentUserId);
        List<SprintResponse> responses = new ArrayList<>();
        for (Sprint sprint : sprintRepository.findByProjectId(projectId)) {
            responses.add(toResponse(sprint));
        }
        return responses;
    }

    @Override
    @Transactional(readOnly = true)
    public List<SprintResponse> listAssignedToMe(UUID currentUserId) {
        UUID workspaceId = projectAccess.requireActiveWorkspace(currentUserId);
        List<SprintResponse> responses = new ArrayList<>();
        for (Sprint sprint : sprintRepository.findAssignedToUserInWorkspace(currentUserId, workspaceId)) {
            responses.add(toResponse(sprint));
        }
        return responses;
    }

    @Override
    @Transactional(readOnly = true)
    public List<SprintResponse> listSprintsForIssue(UUID issueId, UUID currentUserId) {
        Issue issue = issueRepository.findById(issueId)
                .orElseThrow(() -> new ResourceNotFoundException("Issue not found"));
        checkMember(issue.getProjectId(), currentUserId);
        List<SprintResponse> responses = new ArrayList<>();
        for (Sprint sprint : sprintRepository.findByIssueIdOrderByCreatedAtDesc(issueId)) {
            responses.add(toResponse(sprint));
        }
        return responses;
    }

    @Override
    @Transactional(readOnly = true)
    public SprintResponse getSprint(UUID sprintId, UUID currentUserId) {
        Sprint sprint = findSprintOrThrow(sprintId);
        checkMember(sprint.getProjectId(), currentUserId);
        return toResponse(sprint);
    }

    @Override
    @Transactional
    public SprintResponse updateSprint(UUID sprintId, UpdateSprintRequest request, UUID currentUserId) {
        Sprint sprint = findSprintOrThrow(sprintId);
        requireProjectManager(sprint.getProjectId(), currentUserId);
        UUID updatedIssueId = request.isUnlinkIssue() ? null
                : request.getIssueId() == null ? sprint.getIssueId() : request.getIssueId();

        LocalDate updatedStartDate = request.getStartDate() == null
                ? sprint.getStartDate()
                : request.getStartDate();
        LocalDate updatedEndDate = request.getEndDate() == null
                ? sprint.getEndDate()
                : request.getEndDate();
        checkDates(updatedStartDate, updatedEndDate);
        Project project = findProjectForWrite(sprint.getProjectId());
        checkIssueProject(sprint.getProjectId(), updatedIssueId, updatedStartDate, updatedEndDate);
        if (request.getIssueId() != null || request.isUnlinkIssue()) {
            sprint.setIssueId(updatedIssueId);
        }
        checkDatesWithinProject(project, updatedStartDate, updatedEndDate);
        if (request.getName() != null) sprint.setName(request.getName());
        if (request.getGoal() != null) sprint.setGoal(request.getGoal());
        sprint.setStartDate(updatedStartDate);
        sprint.setEndDate(updatedEndDate);
        Sprint updatedSprint = sprintRepository.save(sprint);
        return toResponse(updatedSprint);
    }

    @Override
    @Transactional
    public SprintResponse changeStatus(UUID sprintId, ChangeSprintStatusRequest request, UUID currentUserId) {
        Sprint sprint = findSprintForWrite(sprintId);
        requireProjectManager(sprint.getProjectId(), currentUserId);
        if (sprint.getStatus() == request.status()) {
            return toResponse(sprint);
        }
        sprint.setStatus(request.status());
        Sprint updatedSprint = sprintRepository.save(sprint);
        return toResponse(updatedSprint);
    }

    @Override
    @Transactional
    public SprintResponse startSprint(UUID sprintId, UUID currentUserId) {
        Sprint sprint = findSprintOrThrow(sprintId);
        requireManagerOrAssignedContributor(sprint, currentUserId);
        if (sprint.getStatus() != SprintStatus.PLANNED) {
            throw new BadRequestException("Only a planned sprint can be started");
        }
        if (sprint.getAssignedContributorId() == null) {
            throw new BadRequestException("Only a sprint with an assigned contributor can be started");
        }

        sprint.setStatus(SprintStatus.ACTIVE);
        Sprint updatedSprint = sprintRepository.save(sprint);
        return toResponse(updatedSprint);
    }

    @Override
    @Transactional
    public SprintResponse completeSprint(UUID sprintId, UUID currentUserId) {
        Sprint sprint = findSprintOrThrow(sprintId);
        requireManagerOrAssignedContributor(sprint, currentUserId);
        if (sprint.getStatus() != SprintStatus.ACTIVE) {
            throw new BadRequestException("Only an active sprint can be completed");
        }
        sprint.setStatus(SprintStatus.COMPLETED);
        Sprint updatedSprint = sprintRepository.save(sprint);
        return toResponse(updatedSprint);
    }

    @Override
    @Transactional
    public void deleteSprint(UUID sprintId, UUID currentUserId) {
        Sprint sprint = findSprintOrThrow(sprintId);
        requireProjectManager(sprint.getProjectId(), currentUserId);
        assignmentRequestRepository.deleteBySprintId(sprintId);
        sprintCommentRepository.deleteBySprintId(sprintId);
        sprintRepository.delete(sprint);
    }

    @Override
    @Transactional
    public SprintResponse assignSprint(UUID sprintId, UUID contributorId, UUID currentUserId) {
        Sprint sprint = findSprintForWrite(sprintId);
        requireProjectManager(sprint.getProjectId(), currentUserId);
        checkContributor(sprint.getProjectId(), contributorId);
        checkNotCompleted(sprint);
        if (contributorId.equals(sprint.getAssignedContributorId())) {
            throw new BadRequestException("Sprint is already assigned to this contributor");
        }

        sprint.setAssignedContributorId(contributorId);
        Sprint updatedSprint = sprintRepository.save(sprint);
        rejectRequests(sprint, currentUserId, null);
        notificationPublisher.publish(contributorId, NotificationType.SPRINT_ASSIGNED,
                "Sprint assigned to you", "You were assigned to sprint " + sprint.getName() + ".",
                sprint.getProjectId(), null, currentUserId);
        return toResponse(updatedSprint);
    }

    @Override
    @Transactional
    public SprintAssignmentRequestResponse requestAssignment(UUID sprintId, UUID currentUserId) {
        Sprint sprint = findSprintForWrite(sprintId);
        checkContributor(sprint.getProjectId(), currentUserId);
        checkNotCompleted(sprint);
        if (currentUserId.equals(sprint.getAssignedContributorId())) {
            throw new BadRequestException("Sprint is already assigned to you");
        }
        if (assignmentRequestRepository.existsBySprintIdAndRequestedByAndStatus(
                sprintId, currentUserId, SprintAssignmentRequestStatus.PENDING)) {
            throw new BadRequestException("You already have a pending request for this sprint");
        }

        SprintAssignmentRequest request = new SprintAssignmentRequest();
        request.setSprintId(sprintId);
        request.setRequestedBy(currentUserId);
        SprintAssignmentRequest savedRequest = assignmentRequestRepository.save(request);
        Project project = findProjectForWrite(sprint.getProjectId());
        if (project.getProjectManagerId() != null) {
            notificationPublisher.publish(project.getProjectManagerId(),
                    NotificationType.SPRINT_ASSIGNMENT_REQUESTED,
                    "Sprint assignment requested", "A contributor requested sprint " + sprint.getName() + ".",
                    project.getId(), null, currentUserId);
        }
        return toAssignmentRequestResponse(savedRequest);
    }

    @Override
    @Transactional(readOnly = true)
    public List<SprintAssignmentRequestResponse> listSprintRequests(
            UUID projectId,
            UUID currentUserId) {
        Project project = projectRepository.findById(projectId)
                .orElseThrow(() -> new ResourceNotFoundException("Project not found"));
        requireProjectManager(project, currentUserId);
        List<SprintAssignmentRequestResponse> responses = new ArrayList<>();
        for (SprintAssignmentRequest request : assignmentRequestRepository.findAllForProject(projectId)) {
            responses.add(toAssignmentRequestResponse(request));
        }
        return responses;
    }

    @Override
    @Transactional
    public SprintAssignmentRequestResponse approveRequest(
            UUID sprintId,
            UUID requestId,
            UUID currentUserId) {
        Sprint sprint = findSprintForWrite(sprintId);
        requireProjectManager(sprint.getProjectId(), currentUserId);
        SprintAssignmentRequest request = assignmentRequestRepository.findByIdForUpdate(requestId)
                .orElseThrow(() -> new ResourceNotFoundException("Sprint assignment request not found"));
        if (!request.getSprintId().equals(sprintId)) {
            throw new ResourceNotFoundException("Sprint assignment request not found");
        }
        if (request.getStatus() != SprintAssignmentRequestStatus.PENDING) {
            throw new BadRequestException("Only a pending assignment request can be approved");
        }
        checkContributor(sprint.getProjectId(), request.getRequestedBy());
        checkNotCompleted(sprint);

        sprint.setAssignedContributorId(request.getRequestedBy());
        sprintRepository.save(sprint);
        notificationPublisher.publish(request.getRequestedBy(), NotificationType.SPRINT_ASSIGNED,
                "Sprint request approved", "Your request to join sprint " + sprint.getName() + " was approved.",
                sprint.getProjectId(), null, currentUserId);
        request.setStatus(SprintAssignmentRequestStatus.APPROVED);
        request.setReviewedBy(currentUserId);
        SprintAssignmentRequest approvedRequest = assignmentRequestRepository.save(request);
        rejectRequests(sprint, currentUserId, requestId);
        return toAssignmentRequestResponse(approvedRequest);
    }

    private void requireProjectManager(UUID projectId, UUID userId) {
        Project project = findProjectForWrite(projectId);
        requireProjectManager(project, userId);
    }

    private void requireProjectManager(Project project, UUID userId) {
        projectAccess.requireWorkspaceAndActive(project, userId);
        if (userId == null || (!userId.equals(project.getAdminId())
                && !userId.equals(project.getProjectManagerId()))) {
            throw new ForbiddenException("Only the project admin or project manager can perform this action");
        }
        if (userId.equals(project.getAdminId())) return;
        User manager = userRepository.findById(userId)
                .orElseThrow(() -> new ForbiddenException("Project manager is not a valid user"));
        if (manager.getRole() != Role.PROJECT_MANAGER) {
            throw new ForbiddenException("Project manager must have the PROJECT_MANAGER role");
        }
    }

    private void requireProjectManagerOrTeamLeadorAdmin(Project project, UUID userId) {
        projectAccess.requireWorkspaceAndActive(project, userId);
        if (userId != null && userId.equals(project.getAdminId())) {
            requireProjectManager(project, userId);
            return;
        }
        if (userId != null && userId.equals(project.getProjectManagerId())) {
            requireProjectManager(project, userId);
            return;
        }
        if (userId == null || userId.equals(project.getTeamLeadId())) {
            throw new ForbiddenException("Only the project admin, project manager or team lead can create a sprint");
        }
    }

    private void requireManagerOrAssignedContributor(Sprint sprint, UUID userId) {
        Project project = findProjectOrThrow(sprint.getProjectId());
        if (userId != null && userId.equals(project.getAdminId())) {
            requireProjectManager(project, userId);
            return;
        }
        if (userId != null && userId.equals(project.getProjectManagerId())) {
            requireProjectManager(project, userId);
            return;
        }
        if (userId == null || !userId.equals(sprint.getAssignedContributorId())) {
            throw new ForbiddenException("Only the project admin, project manager or assigned contributor can update sprint status");
        }
        checkContributor(sprint.getProjectId(), userId);
    }

    private void checkMember(UUID projectId, UUID userId) {
        Project project = projectRepository.findById(projectId)
                .orElseThrow(() -> new ResourceNotFoundException("Project not found"));
        projectAccess.requireWorkspaceAndActive(project, userId);
        if (userId == null) {
            throw new ForbiddenException("You are not a member of this project");
        }
        boolean projectRoleMember = userId.equals(project.getCreatedBy())
                || userId.equals(project.getAdminId())
                || userId.equals(project.getProjectManagerId())
                || userId.equals(project.getTeamLeadId());
        if (!projectRoleMember && !projectMemberRepository.existsByProjectIdAndUserId(projectId, userId)) {
            throw new ForbiddenException("You are not a member of this project");
        }
    }

    private void checkContributor(UUID projectId, UUID contributorId) {
        Project project = findProjectOrThrow(projectId);
        projectAccess.requireWorkspaceAndActive(project, contributorId);
        User contributor = userRepository.findById(contributorId)
                .orElseThrow(() -> new ResourceNotFoundException("Contributor not found"));
        if (contributor.getRole() != Role.CONTRIBUTOR) {
            throw new ForbiddenException("User is not a contributor");
        }
        ProjectMember projectMember = projectMemberRepository.findByProjectIdAndUserId(projectId, contributorId)
                .orElseThrow(() -> new ForbiddenException("Contributor is not a member of this project"));
        if (projectMember.getRole() != Role.CONTRIBUTOR) {
            throw new ForbiddenException("Contributor is not a member of this project");
        }
    }

    private void checkDates(LocalDate startDate, LocalDate endDate) {
        if (startDate != null && endDate != null && endDate.isBefore(startDate)) {
            throw new BadRequestException("Sprint end date cannot be before start date");
        }
    }

    private void checkIssueProject(UUID projectId, UUID issueId, LocalDate taskStartDate, LocalDate taskEndDate) {
        if (issueId == null) return;
        Issue issue = issueRepository.findById(issueId)
                .orElseThrow(() -> new ResourceNotFoundException("Issue not found"));
        if (!projectId.equals(issue.getProjectId()) || issue.getDeletedAt() != null) {
            throw new BadRequestException("Linked issue must be an active issue in this project");
        }
        if (issue.getStartDate() != null && taskStartDate != null && taskStartDate.isBefore(issue.getStartDate())) {
            throw new BadRequestException("Task start date cannot be before the issue start date");
        }
        if (issue.getDueDate() != null && taskEndDate != null && taskEndDate.isAfter(issue.getDueDate())) {
            throw new BadRequestException("Task end date cannot be after the issue due date");
        }
    }

    private SprintResponse toResponse(Sprint sprint) {
        Issue issue = sprint.getIssueId() == null ? null : issueRepository.findById(sprint.getIssueId()).orElse(null);
        String contributorName = sprint.getAssignedContributorId() == null ? null
                : userRepository.findById(sprint.getAssignedContributorId()).map(User::getName).orElse(null);
        String creatorName = userRepository.findById(sprint.getCreatedBy()).map(User::getName).orElse(null);
        return SprintResponse.fromEntity(sprint, issue == null ? null : issue.getTitle(),
                issue == null ? null : issue.getIssueNumber(),
                contributorName, creatorName);
    }

    private SprintAssignmentRequestResponse toAssignmentRequestResponse(SprintAssignmentRequest request) {
        String requesterName = userRepository.findById(request.getRequestedBy()).map(User::getName).orElse(null);
        String reviewerName = request.getReviewedBy() == null ? null
                : userRepository.findById(request.getReviewedBy()).map(User::getName).orElse(null);
        return SprintAssignmentRequestResponse.fromEntity(request, requesterName, reviewerName);
    }

    private void checkDatesWithinProject(Project project, LocalDate startDate, LocalDate endDate) {
        if (project.getStartDate() != null && startDate != null
                && startDate.isBefore(project.getStartDate())) {
            throw new BadRequestException("Sprint start date cannot be before the project start date");
        }
        if (project.getDeadline() != null && endDate != null
                && endDate.isAfter(project.getDeadline())) {
            throw new BadRequestException("Sprint end date cannot be after the project deadline");
        }
    }

    private Project findProjectForWrite(UUID projectId) {
        return projectRepository.findByIdForUpdate(projectId)
                .orElseThrow(() -> new ResourceNotFoundException("Project not found"));
    }

    private Project findProjectOrThrow(UUID projectId) {
        return projectRepository.findById(projectId)
                .orElseThrow(() -> new ResourceNotFoundException("Project not found"));
    }

    private void checkNotCompleted(Sprint sprint) {
        if (sprint.getStatus() == SprintStatus.COMPLETED) {
            throw new BadRequestException("A completed sprint cannot be assigned");
        }
    }

    private void rejectRequests(Sprint sprint, UUID reviewedBy, UUID exceptRequestId) {
        List<SprintAssignmentRequest> pendingRequests = assignmentRequestRepository.findBySprintIdAndStatus(
                sprint.getId(), SprintAssignmentRequestStatus.PENDING);
        for (SprintAssignmentRequest request : pendingRequests) {
            if (request.getId().equals(exceptRequestId)) {
                continue;
            }
            request.setStatus(SprintAssignmentRequestStatus.REJECTED);
            request.setReviewedBy(reviewedBy);
        }
        assignmentRequestRepository.saveAll(pendingRequests);
    }

    private Sprint findSprintForWrite(UUID sprintId) {
        return sprintRepository.findByIdForUpdate(sprintId)
                .orElseThrow(() -> new ResourceNotFoundException("Sprint not found"));
    }

    private Sprint findSprintOrThrow(UUID sprintId) {
        return sprintRepository.findById(sprintId)
                .orElseThrow(() -> new ResourceNotFoundException("Sprint not found"));
    }

}
