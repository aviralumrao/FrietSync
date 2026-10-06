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
import com.frietsync.backend.entity.user.Role;
import com.frietsync.backend.entity.user.User;
import com.frietsync.backend.exception.ForbiddenException;
import com.frietsync.backend.exception.BadRequestException;
import com.frietsync.backend.repository.project.ProjectRepository;
import com.frietsync.backend.repository.project.ProjectMemberRepository;
import com.frietsync.backend.repository.sprint.SprintAssignmentRequestRepository;
import com.frietsync.backend.repository.sprint.SprintRepository;
import com.frietsync.backend.repository.user.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class SprintServiceImplTest {

    private final SprintRepository sprintRepository = mock(SprintRepository.class);
    private final ProjectRepository projectRepository = mock(ProjectRepository.class);
    private final ProjectMemberRepository projectMemberRepository = mock(ProjectMemberRepository.class);
    private final UserRepository userRepository = mock(UserRepository.class);
    private final SprintAssignmentRequestRepository requestRepository =
            mock(SprintAssignmentRequestRepository.class);
    private final SprintServiceImpl sprintService = new SprintServiceImpl(
            sprintRepository,
            projectRepository,
            projectMemberRepository,
            userRepository,
            requestRepository);

    private final UUID sprintId = UUID.randomUUID();
    private final UUID projectId = UUID.randomUUID();
    private final UUID managerId = UUID.randomUUID();
    private final UUID contributorId = UUID.randomUUID();
    private Sprint sprint;

    @BeforeEach
    void setUp() {
        sprint = new Sprint();
        sprint.setId(sprintId);
        sprint.setProjectId(projectId);
        sprint.setName("Sprint 1");
        sprint.setStatus(SprintStatus.PLANNED);
        Project project = new Project();
        project.setProjectManagerId(managerId);
        project.setSprintCounter(3);
        project.setCreatedBy(UUID.randomUUID());
        project.setAdminId(UUID.randomUUID());
        project.setTeamLeadId(UUID.randomUUID());

        when(sprintRepository.findById(sprintId)).thenReturn(Optional.of(sprint));
        when(projectRepository.findById(projectId)).thenReturn(Optional.of(project));
        when(sprintRepository.findMaxSprintNumber(projectId)).thenReturn(3);
        when(sprintRepository.save(any(Sprint.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(requestRepository.save(any(SprintAssignmentRequest.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
        when(projectMemberRepository.existsByProjectIdAndUserId(projectId, contributorId)).thenReturn(true);
    }

    @Test
    void allowsProjectMemberToReadSprint() {
        when(projectMemberRepository.existsByProjectIdAndUserId(projectId, contributorId)).thenReturn(true);

        SprintResponse response = sprintService.getSprint(sprintId, contributorId);

        assertEquals(sprintId, response.getId());
    }

    @Test
    void rejectsNonMemberFromReadingSprint() {
        when(projectMemberRepository.existsByProjectIdAndUserId(projectId, contributorId)).thenReturn(false);

        assertThrows(ForbiddenException.class, () -> sprintService.getSprint(sprintId, contributorId));
    }

    @Test
    void rejectsNonProjectManagerFromEditingSprint() {
        assertThrows(ForbiddenException.class, () -> sprintService.startSprint(sprintId, contributorId));
        verify(sprintRepository, never()).save(any(Sprint.class));
    }

    @Test
    void allowsProjectManagerToAssignContributor() {
        allowContributor(contributorId);

        SprintResponse response = sprintService.assignSprint(sprintId, contributorId, managerId);

        assertEquals(contributorId, response.getAssignedContributorId());
    }

    @Test
    void rejectsAssigningUserWhoIsNotContributor() {
        User user = new User();
        user.setRole(Role.REPORTER);
        when(userRepository.findById(contributorId)).thenReturn(Optional.of(user));

        assertThrows(ForbiddenException.class, () -> sprintService.assignSprint(sprintId, contributorId, managerId));
        verify(sprintRepository, never()).save(any(Sprint.class));
    }

    @Test
    void rejectsAssigningContributorWhoIsNotAProjectMember() {
        allowContributor(contributorId);
        when(projectMemberRepository.existsByProjectIdAndUserId(projectId, contributorId)).thenReturn(false);

        assertThrows(ForbiddenException.class,
                () -> sprintService.assignSprint(sprintId, contributorId, managerId));
        verify(sprintRepository, never()).save(any(Sprint.class));
    }

    @Test
    void allowsContributorToCreateAssignmentRequest() {
        allowContributor(contributorId);
        when(requestRepository.existsBySprintIdAndRequestedByAndStatus(
                sprintId, contributorId, SprintAssignmentRequestStatus.PENDING)).thenReturn(false);
        when(requestRepository.save(any(SprintAssignmentRequest.class))).thenAnswer(invocation -> {
            SprintAssignmentRequest request = invocation.getArgument(0);
            request.setId(UUID.randomUUID());
            return request;
        });

        SprintAssignmentRequestResponse response =
                sprintService.requestAssignment(sprintId, contributorId);

        assertEquals(sprintId, response.getSprintId());
        assertEquals(contributorId, response.getRequestedBy());
        assertEquals(SprintAssignmentRequestStatus.PENDING, response.getStatus());
    }

    @Test
    void allowsContributorToRequestReassignmentOfAssignedSprint() {
        UUID currentAssignee = UUID.randomUUID();
        sprint.setAssignedContributorId(currentAssignee);
        allowContributor(contributorId);
        when(requestRepository.existsBySprintIdAndRequestedByAndStatus(
                sprintId, contributorId, SprintAssignmentRequestStatus.PENDING)).thenReturn(false);
        when(requestRepository.save(any(SprintAssignmentRequest.class))).thenAnswer(invocation -> {
            SprintAssignmentRequest request = invocation.getArgument(0);
            request.setId(UUID.randomUUID());
            return request;
        });

        SprintAssignmentRequestResponse response = sprintService.requestAssignment(sprintId, contributorId);

        assertEquals(SprintAssignmentRequestStatus.PENDING, response.getStatus());
        assertEquals(currentAssignee, sprint.getAssignedContributorId());
    }

    @Test
    void approvesPendingAssignmentAndRejectsOtherRequests() {
        allowContributor(contributorId);
        sprint.setAssignedContributorId(UUID.randomUUID());
        UUID requestId = UUID.randomUUID();
        SprintAssignmentRequest pending = new SprintAssignmentRequest();
        pending.setId(requestId);
        pending.setSprintId(sprintId);
        pending.setRequestedBy(contributorId);
        pending.setStatus(SprintAssignmentRequestStatus.PENDING);
        when(requestRepository.findByIdAndSprintId(requestId, sprintId)).thenReturn(Optional.of(pending));
        SprintAssignmentRequest other = new SprintAssignmentRequest();
        other.setId(UUID.randomUUID());
        other.setSprintId(sprintId);
        other.setRequestedBy(UUID.randomUUID());
        other.setStatus(SprintAssignmentRequestStatus.PENDING);
        when(requestRepository.findBySprintIdAndStatus(sprintId, SprintAssignmentRequestStatus.PENDING))
                .thenReturn(List.of(pending, other));

        SprintAssignmentRequestResponse response =
                sprintService.approveRequest(sprintId, requestId, managerId);

        assertEquals(SprintAssignmentRequestStatus.APPROVED, response.getStatus());
        assertEquals(contributorId, sprint.getAssignedContributorId());
        assertEquals(SprintAssignmentRequestStatus.REJECTED, other.getStatus());
        verify(requestRepository).saveAll(List.of(pending, other));
    }

    @Test
    void rejectsCreateWhenEndDatePrecedesStartDate() {
        CreateSprintRequest request = new CreateSprintRequest();
        request.setName("Sprint 4");
        request.setStartDate(LocalDate.of(2026, 10, 20));
        request.setEndDate(LocalDate.of(2026, 10, 19));

        assertThrows(BadRequestException.class, () -> sprintService.createSprint(projectId, request, managerId));
        verify(sprintRepository, never()).save(any(Sprint.class));
    }

    @Test
    void rejectsCreateWhenSprintIsOutsideProjectDates() {
        Project project = new Project();
        project.setProjectManagerId(managerId);
        project.setStartDate(LocalDate.of(2026, 10, 1));
        project.setDeadline(LocalDate.of(2026, 10, 31));
        when(projectRepository.findById(projectId)).thenReturn(Optional.of(project));
        CreateSprintRequest request = new CreateSprintRequest();
        request.setName("Out of range");
        request.setStartDate(LocalDate.of(2026, 9, 30));
        request.setEndDate(LocalDate.of(2026, 10, 10));

        assertThrows(BadRequestException.class,
                () -> sprintService.createSprint(projectId, request, managerId));
        verify(sprintRepository, never()).save(any(Sprint.class));
    }

    @Test
    void continuesSprintSequenceAfterEarlierSprintDeletion() {
        CreateSprintRequest request = new CreateSprintRequest();
        request.setName("Sprint 4");
        request.setStartDate(LocalDate.of(2026, 10, 20));
        request.setEndDate(LocalDate.of(2026, 10, 27));
        when(sprintRepository.findMaxSprintNumber(projectId)).thenReturn(3);
        when(sprintRepository.save(any(Sprint.class))).thenAnswer(invocation -> {
            Sprint createdSprint = invocation.getArgument(0);
            createdSprint.setId(UUID.randomUUID());
            return createdSprint;
        });

        sprintService.createSprint(projectId, request, managerId);

        verify(sprintRepository).save(org.mockito.ArgumentMatchers.argThat(
                createdSprint -> createdSprint.getSprintNumber() == 4));
    }

    @Test
    void rejectsPatchThatWouldCreateAnInvalidDateRange() {
        sprint.setStartDate(LocalDate.of(2026, 10, 20));
        sprint.setEndDate(LocalDate.of(2026, 10, 30));
        UpdateSprintRequest request = new UpdateSprintRequest();
        request.setEndDate(LocalDate.of(2026, 10, 19));

        assertThrows(BadRequestException.class, () -> sprintService.updateSprint(sprintId, request, managerId));
        assertEquals(LocalDate.of(2026, 10, 30), sprint.getEndDate());
        verify(sprintRepository, never()).save(any(Sprint.class));
    }

    private void allowContributor(UUID userId) {
        User contributor = new User();
        contributor.setRole(Role.CONTRIBUTOR);
        when(userRepository.findById(userId)).thenReturn(Optional.of(contributor));
    }
}
