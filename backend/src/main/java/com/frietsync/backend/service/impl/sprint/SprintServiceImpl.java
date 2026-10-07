package com.frietsync.backend.service.impl.sprint;

import com.frietsync.backend.dto.sprint.CreateSprintRequest;
import com.frietsync.backend.dto.sprint.SprintAssignmentRequestResponse;
import com.frietsync.backend.dto.sprint.SprintResponse;
import com.frietsync.backend.dto.sprint.UpdateSprintRequest;
import com.frietsync.backend.entity.project.Project;
import com.frietsync.backend.entity.sprint.Sprint;
import com.frietsync.backend.entity.sprint.SprintAssignmentRequest;
import com.frietsync.backend.entity.sprint.SprintAssignmentRequestStatus;
import com.frietsync.backend.entity.sprint.SprintStatus;
import com.frietsync.backend.entity.project.ProjectMember;
import com.frietsync.backend.entity.user.Role;
import com.frietsync.backend.entity.user.User;
import com.frietsync.backend.exception.BadRequestException;
import com.frietsync.backend.exception.ForbiddenException;
import com.frietsync.backend.exception.ResourceNotFoundException;
import com.frietsync.backend.repository.project.ProjectRepository;
import com.frietsync.backend.repository.project.ProjectMemberRepository;
import com.frietsync.backend.repository.sprint.SprintAssignmentRequestRepository;
import com.frietsync.backend.repository.sprint.SprintRepository;
import com.frietsync.backend.repository.user.UserRepository;
import com.frietsync.backend.service.sprint.SprintService;
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
    private final UserRepository userRepository;
    private final SprintAssignmentRequestRepository assignmentRequestRepository;

    @Override
    @Transactional
    public SprintResponse createSprint(UUID projectId, CreateSprintRequest request, UUID currentUserId) {
        Project project = projectRepository.findById(projectId)
                .orElseThrow(() -> new ResourceNotFoundException("Project not found"));
        requireProjectManager(project, currentUserId);
        checkDates(request.getStartDate(), request.getEndDate());
        checkDatesWithinProject(project, request.getStartDate(), request.getEndDate());
        Integer highestNumber = sprintRepository.findMaxSprintNumber(projectId);
        int nextNumber = Math.max(project.getSprintCounter(), highestNumber == null ? 0 : highestNumber) + 1;
        Sprint sprint = new Sprint();
        sprint.setProjectId(projectId);
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
        return SprintResponse.fromEntity(savedSprint);
    }

    @Override
    @Transactional(readOnly = true)
    public List<SprintResponse> listSprints(UUID projectId, UUID currentUserId) {
        checkMember(projectId, currentUserId);
        return sprintRepository.findByProjectId(projectId).stream()
                .map(SprintResponse::fromEntity)
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public SprintResponse getSprint(UUID sprintId, UUID currentUserId) {
        Sprint sprint = findSprintOrThrow(sprintId);
        checkMember(sprint.getProjectId(), currentUserId);
        return SprintResponse.fromEntity(sprint);
    }

    @Override
    @Transactional
    public SprintResponse updateSprint(UUID sprintId, UpdateSprintRequest request, UUID currentUserId) {
        Sprint sprint = findSprintOrThrow(sprintId);
        requireProjectManager(sprint.getProjectId(), currentUserId);

        LocalDate updatedStartDate = request.getStartDate() == null
                ? sprint.getStartDate()
                : request.getStartDate();
        LocalDate updatedEndDate = request.getEndDate() == null
                ? sprint.getEndDate()
                : request.getEndDate();
        checkDates(updatedStartDate, updatedEndDate);
        Project project = findProjectForWrite(sprint.getProjectId());
        checkDatesWithinProject(project, updatedStartDate, updatedEndDate);
        if (request.getName() != null) sprint.setName(request.getName());
        if (request.getGoal() != null) sprint.setGoal(request.getGoal());
        sprint.setStartDate(updatedStartDate);
        sprint.setEndDate(updatedEndDate);
        Sprint updatedSprint = sprintRepository.save(sprint);
        return SprintResponse.fromEntity(updatedSprint);
    }

    @Override
    @Transactional
    public SprintResponse startSprint(UUID sprintId, UUID currentUserId) {
        Sprint sprint = findSprintOrThrow(sprintId);
        requireManagerOrAssignedContributor(sprint, currentUserId);
        if (sprint.getStatus() != SprintStatus.PLANNED) {
            throw new BadRequestException("Only a planned sprint can be started");
        }

        sprint.setStatus(SprintStatus.ACTIVE);
        Sprint updatedSprint = sprintRepository.save(sprint);
        return SprintResponse.fromEntity(updatedSprint);
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
        return SprintResponse.fromEntity(updatedSprint);
    }

    @Override
    @Transactional
    public void deleteSprint(UUID sprintId, UUID currentUserId) {
        Sprint sprint = findSprintOrThrow(sprintId);
        requireProjectManager(sprint.getProjectId(), currentUserId);
        assignmentRequestRepository.deleteBySprintId(sprintId);
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
        return SprintResponse.fromEntity(updatedSprint);
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
        return SprintAssignmentRequestResponse.fromEntity(savedRequest);
    }

    @Override
    @Transactional(readOnly = true)
    public List<SprintAssignmentRequestResponse> listSprintRequests(
            UUID projectId,
            UUID currentUserId) {
        requireProjectManager(projectId, currentUserId);
        List<SprintAssignmentRequestResponse> responses = new ArrayList<>();
        for (SprintAssignmentRequest request : assignmentRequestRepository.findAllForProject(projectId)) {
            responses.add(SprintAssignmentRequestResponse.fromEntity(request));
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
        request.setStatus(SprintAssignmentRequestStatus.APPROVED);
        request.setReviewedBy(currentUserId);
        SprintAssignmentRequest approvedRequest = assignmentRequestRepository.save(request);
        rejectRequests(sprint, currentUserId, requestId);
        return SprintAssignmentRequestResponse.fromEntity(approvedRequest);
    }

    private void requireProjectManager(UUID projectId, UUID userId) {
        Project project = findProjectForWrite(projectId);
        requireProjectManager(project, userId);
    }

    private void requireProjectManager(Project project, UUID userId) {
        if (userId == null || !userId.equals(project.getProjectManagerId())) {
            throw new ForbiddenException("Only the project manager can perform this action");
        }
        User manager = userRepository.findById(userId)
                .orElseThrow(() -> new ForbiddenException("Project manager is not a valid user"));
        if (manager.getRole() != Role.PROJECT_MANAGER) {
            throw new ForbiddenException("Project manager must have the PROJECT_MANAGER role");
        }
    }

    private void requireManagerOrAssignedContributor(Sprint sprint, UUID userId) {
        Project project = findProjectOrThrow(sprint.getProjectId());
        if (userId != null && userId.equals(project.getProjectManagerId())) {
            requireProjectManager(project, userId);
            return;
        }
        if (userId == null || !userId.equals(sprint.getAssignedContributorId())) {
            throw new ForbiddenException("Only the project manager or assigned contributor can update sprint status");
        }
        checkContributor(sprint.getProjectId(), userId);
    }

    private void checkMember(UUID projectId, UUID userId) {
        Project project = projectRepository.findById(projectId)
                .orElseThrow(() -> new ResourceNotFoundException("Project not found"));
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
