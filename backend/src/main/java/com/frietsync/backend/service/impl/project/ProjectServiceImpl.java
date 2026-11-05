package com.frietsync.backend.service.impl.project;

import com.frietsync.backend.dto.project.MemberResponse;
import com.frietsync.backend.dto.project.ProjectInviteRequest;
import com.frietsync.backend.dto.invite.InviteResponse;
import com.frietsync.backend.dto.project.ProjectRequest;
import com.frietsync.backend.dto.project.ProjectResponse;
import com.frietsync.backend.dto.project.ProjectListResponse;
import com.frietsync.backend.entity.project.Project;
import com.frietsync.backend.entity.project.ProjectMember;
import com.frietsync.backend.entity.project.ProjectStatus;
import com.frietsync.backend.entity.notification.NotificationType;
import com.frietsync.backend.entity.issue.AssignmentRequestStatus;
import com.frietsync.backend.entity.issue.IssueAssignee;
import com.frietsync.backend.entity.issue.Issue;
import com.frietsync.backend.entity.issue.IssueAssignmentRequest;
import com.frietsync.backend.entity.issue.IssueHistoryAction;
import com.frietsync.backend.entity.sprint.SprintAssignmentRequestStatus;
import com.frietsync.backend.entity.sprint.SprintStatus;
import com.frietsync.backend.entity.user.Role;
import com.frietsync.backend.entity.user.User;
import com.frietsync.backend.entity.workspace.Workspace;
import com.frietsync.backend.exception.BadRequestException;
import com.frietsync.backend.exception.ForbiddenException;
import com.frietsync.backend.exception.ResourceNotFoundException;
import com.frietsync.backend.exception.StorageServiceException;
import com.frietsync.backend.repository.chat.ChatMessageRepository;
import com.frietsync.backend.repository.meeting.MeetingAttendeeRepository;
import com.frietsync.backend.repository.meeting.MeetingRepository;
import com.frietsync.backend.repository.issue.IssueAssigneeRepository;
import com.frietsync.backend.repository.issue.IssueAssignmentRequestRepository;
import com.frietsync.backend.repository.issue.IssueRepository;
import com.frietsync.backend.repository.activity.ProjectActivityRepository;
import com.frietsync.backend.repository.project.MilestoneRepository;
import com.frietsync.backend.repository.project.ProjectMemberRepository;
import com.frietsync.backend.repository.project.ProjectRepository;
import com.frietsync.backend.repository.project.ProjectGitHubRepositoryRepository;
import com.frietsync.backend.repository.document.ProjectDocumentRepository;
import com.frietsync.backend.repository.sprint.SprintAssignmentRequestRepository;
import com.frietsync.backend.repository.sprint.SprintRepository;
import com.frietsync.backend.repository.user.UserRepository;
import com.frietsync.backend.repository.workspace.WorkspaceRepository;
import com.frietsync.backend.service.project.ProjectService;
import com.frietsync.backend.service.invite.InviteService;
import com.frietsync.backend.service.issue.IssueService;
import com.frietsync.backend.service.project.ReleaseService;
import com.frietsync.backend.service.impl.project.ProjectAccess;
import com.frietsync.backend.service.impl.user.ProfilePictureUrlService;
import com.frietsync.backend.service.impl.storage.S3UploadValidator;
import com.frietsync.backend.service.notification.NotificationPublisher;
import com.frietsync.backend.service.impl.issue.IssueHistoryRecorder;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.web.multipart.MultipartFile;
import software.amazon.awssdk.core.exception.SdkClientException;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.model.S3Exception;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import software.amazon.awssdk.services.s3.presigner.model.GetObjectPresignRequest;

import java.time.Instant;
import java.time.LocalDate;
import java.time.Duration;
import java.io.IOException;
import java.util.*;
import java.util.stream.Stream;

@Service
@RequiredArgsConstructor
public class ProjectServiceImpl implements ProjectService {

    private final ProjectRepository projectRepository;
    private final ProjectActivityRepository projectActivityRepository;
    private final ProjectGitHubRepositoryRepository projectGitHubRepositoryRepository;
    private final ProjectDocumentRepository projectDocumentRepository;
    private final UserRepository userRepository;
    private final IssueRepository issueRepository;
    private final IssueAssigneeRepository issueAssigneeRepository;
    private final IssueAssignmentRequestRepository issueAssignmentRequestRepository;
    private final IssueHistoryRecorder issueHistoryRecorder;
    private final WorkspaceRepository workspaceRepository;
    private final ProjectMemberRepository projectMemberRepository;
    private final ChatMessageRepository chatMessageRepository;
    private final MeetingRepository meetingRepository;
    private final MeetingAttendeeRepository meetingAttendeeRepository;
    private final MilestoneRepository milestoneRepository;
    private final SprintRepository sprintRepository;
    private final SprintAssignmentRequestRepository sprintAssignmentRequestRepository;
    private final InviteService inviteService;
    private final IssueService issueService;
    private final ReleaseService releaseService;
    private final ProjectAccess projectAccess;
    private final NotificationPublisher notificationPublisher;
    private final ProfilePictureUrlService profilePictureUrlService;
    private final com.frietsync.backend.service.chat.ChatAttachmentService chatAttachmentService;
    private final S3Client s3Client;
    private final S3Presigner s3Presigner;
    private final com.frietsync.backend.service.impl.issue.S3ObjectCleanupService cleanupService;
    private final com.frietsync.backend.service.security.VirusScanService virusScanService;

    @Value("${aws.s3.bucket:}")
    private String bucketName;

    @Override
    @Transactional
    public ProjectResponse create(ProjectRequest request, UUID userId) {
        User admin = userRepository.findByIdForUpdate(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));
        if (admin.getRole() != Role.ADMIN) {
            throw new ForbiddenException("Only workspace admins can create projects");
        }
        if (admin.getWorkspaceId() == null) {
            throw new BadRequestException("Join or create a workspace before creating a project");
        }
        Workspace workspace = workspaceRepository.findByIdForUpdate(admin.getWorkspaceId())
                .orElseThrow(() -> new BadRequestException("Workspace not found"));
        if (workspace.isPendingDeletion()) {
            throw new BadRequestException("Workspace is pending deletion");
        }
        if (!userId.equals(workspace.getAdminId())) {
            throw new ForbiddenException("Only the workspace admin can create projects");
        }
        Project project = new Project();
        project.setCreatedBy(userId);
        project.setWorkspaceId(workspace.getId());
        project.setAdminId(workspace.getAdminId());
        apply(project, request, userId, workspace.getId());
        Project saved = projectRepository.save(project);
        notifyProjectRole(saved.getProjectManagerId(), saved.getProjectManagerId(), saved, "project manager", userId);
        notifyProjectRole(saved.getTeamLeadId(), saved.getTeamLeadId(), saved, "team lead", userId);
        return toResponse(saved);
    }

    private UUID findProjectManagerId(String email, UUID workspaceId) {
        if (email == null || email.isBlank()) {
            return null;
        }
        User manager = userRepository.findByEmailIgnoreCase(email.trim())
                .orElseThrow(() -> new BadRequestException("No user found with email: " + email));
        if (manager.getRole() != Role.PROJECT_MANAGER) {
            throw new BadRequestException("Selected user must have the PROJECT_MANAGER role");
        }
        if (!workspaceId.equals(manager.getWorkspaceId())) {
            throw new BadRequestException("Project manager must belong to the project's workspace");
        }
        return manager.getId();
    }
    @Override
    public List<ProjectListResponse> myProjects(UUID userId) {
        UUID workspaceId = projectAccess.requireActiveWorkspace(userId);
        List<ProjectListResponse> responses = new ArrayList<>();
        for (Project project : projectRepository.findAllForUser(userId)) {
            if (workspaceId.equals(project.getWorkspaceId())) {
                responses.add(toListResponse(project));
            }
        }
        return responses;
    }

    private ProjectListResponse toListResponse(Project project) {
        long totalSprints = sprintRepository.countByProjectIdAndDeletedAtIsNull(project.getId());
        long completedSprints = sprintRepository.countByProjectIdAndStatusAndDeletedAtIsNull(
                project.getId(), SprintStatus.COMPLETED);
        double progress = totalSprints == 0 ? 0 : completedSprints * 100.0 / totalSprints;
        long issueCount = issueRepository.countByProjectIdAndDeletedAtIsNull(project.getId());
        ProjectListResponse response = ProjectListResponse.fromEntity(project, totalSprints, issueCount, progress);
        response.setProfilePictureUrl(projectProfilePictureUrl(project));
        return response;
    }

    @Override
    public ProjectResponse get(UUID projectId, UUID userId) {
        Project project = find(projectId);
        projectAccess.requireWorkspaceAndActive(project, userId);
        if (!isInvolved(project, userId)) {
            throw new ForbiddenException("You don't have access to this project");
        }
        return toResponse(project);
    }

    @Override
    @Transactional
    public ProjectResponse update(UUID projectId, ProjectRequest request, UUID userId) {
        Project project = projectRepository.findByIdForUpdate(projectId)
                .orElseThrow(() -> new ResourceNotFoundException("Project not found"));
        projectAccess.requireWorkspaceAndActive(project, userId);
        boolean canEdit = userId.equals(project.getAdminId())
                || userId.equals(project.getProjectManagerId());
        if (!canEdit) {
            throw new ForbiddenException("Only the admin or project manager can edit this project");
        }
        UUID previousManagerId = project.getProjectManagerId();
        UUID previousTeamLeadId = project.getTeamLeadId();
        apply(project, request, userId, project.getWorkspaceId());
        Project saved = projectRepository.save(project);
        if (!Objects.equals(previousManagerId, saved.getProjectManagerId())) {
            notifyProjectRole(previousManagerId, saved.getProjectManagerId(), saved, "project manager", userId);
        }
        if (!Objects.equals(previousTeamLeadId, saved.getTeamLeadId())) {
            notifyProjectRole(previousTeamLeadId, saved.getTeamLeadId(), saved, "team lead", userId);
        }
        return toResponse(saved);
    }

    @Override
    @Transactional
    public ProjectResponse uploadProfilePicture(UUID projectId, MultipartFile profilePicture, UUID userId) {
        Project project = projectRepository.findByIdForUpdate(projectId)
                .orElseThrow(() -> new ResourceNotFoundException("Project not found"));
        projectAccess.requireWorkspaceAndActive(project, userId);
        requireProjectPictureEditor(project, userId);
        if (profilePicture == null || profilePicture.isEmpty()) {
            throw new BadRequestException("Project profile picture must not be empty");
        }

        String previousKey = project.getProfilePictureKey();
        project.setProfilePictureKey(uploadProfilePicture(projectId, profilePicture));
        Project saved = projectRepository.save(project);
        enqueueStoredFileDeletion(previousKey);
        return toResponse(saved);
    }

    @Override
    @Transactional
    public ProjectResponse removeProfilePicture(UUID projectId, UUID userId) {
        Project project = projectRepository.findByIdForUpdate(projectId)
                .orElseThrow(() -> new ResourceNotFoundException("Project not found"));
        projectAccess.requireWorkspaceAndActive(project, userId);
        requireProjectPictureEditor(project, userId);

        String previousKey = project.getProfilePictureKey();
        project.setProfilePictureKey(null);
        Project saved = projectRepository.save(project);
        enqueueStoredFileDeletion(previousKey);
        return toResponse(saved);
    }

    private void requireProjectPictureEditor(Project project, UUID userId) {
        if (!userId.equals(project.getAdminId()) && !userId.equals(project.getProjectManagerId())) {
            throw new ForbiddenException("Only the admin or project manager can edit this project");
        }
    }

    private ProjectResponse toResponse(Project project) {
        long totalSprints = sprintRepository.countByProjectIdAndDeletedAtIsNull(project.getId());
        long completedSprints = sprintRepository.countByProjectIdAndStatusAndDeletedAtIsNull(
                project.getId(), SprintStatus.COMPLETED);
        double progress = 0;
        if (totalSprints > 0) {
            progress = completedSprints * 100.0 / totalSprints;
        }
        ProjectResponse response = ProjectResponse.fromEntity(project, progress);
        projectGitHubRepositoryRepository.findByProjectId(project.getId()).ifPresent(repository -> {
            response.setGithubRepositoryOwner(repository.getRepositoryOwner());
            response.setGithubRepositoryName(repository.getRepositoryName());
        });
        response.setCreatedByName(userRepository.findById(project.getCreatedBy()).map(User::getName).orElse(null));
        response.setProfilePictureUrl(projectProfilePictureUrl(project));
        response.setProjectManager(userSummary(project.getProjectManagerId()));
        response.setTeamLead(userSummary(project.getTeamLeadId()));
        return response;
    }

    private String projectProfilePictureUrl(Project project) {
        if (project.getProfilePictureKey() == null || bucketName == null || bucketName.isBlank()) {
            return null;
        }
        GetObjectRequest getRequest = GetObjectRequest.builder().bucket(bucketName)
                .key(project.getProfilePictureKey()).build();
        GetObjectPresignRequest presignRequest = GetObjectPresignRequest.builder()
                .signatureDuration(Duration.ofHours(1)).getObjectRequest(getRequest).build();
        return s3Presigner.presignGetObject(presignRequest).url().toString();
    }

    private ProjectResponse.UserSummary userSummary(UUID userId) {
        if (userId == null) return null;
        return userRepository.findById(userId)
                .map(user -> ProjectResponse.UserSummary.builder()
                        .id(user.getId())
                        .name(user.getName())
                        .role(user.getRole().name())
                        .profilePictureUrl(profilePictureUrlService.getUrl(user))
                        .build())
                .orElse(null);
    }

    private void notifyProjectRole(UUID previousUserId, UUID assignedUserId, Project project,
                                   String roleName, UUID actorId) {
        Set<UUID> recipients = new LinkedHashSet<>();
        if (previousUserId != null) recipients.add(previousUserId);
        if (assignedUserId != null) recipients.add(assignedUserId);
        for (UUID recipient : recipients) {
            String message = recipient.equals(assignedUserId)
                    ? "You are now the " + roleName + " for project " + project.getName() + "."
                    : "Your " + roleName + " role was removed from project " + project.getName() + ".";
            notificationPublisher.publish(recipient, NotificationType.PROJECT_ROLE_CHANGED,
                    "Project role changed", message, project.getId(), null, actorId);
        }
    }

    @Override
    @Transactional
    public void delete(UUID projectId, UUID userId) {
        Project project = projectRepository.findByIdForUpdate(projectId)
                .orElseThrow(() -> new ResourceNotFoundException("Project not found"));
        projectAccess.requireWorkspaceAndActive(project, userId);
        if (!userId.equals(project.getAdminId())) {
            throw new ForbiddenException("Only the admin can delete this project");
        }
        if (project.getDeletedAt() == null) {
            project.setDeletedAt(Instant.now());
            project.setStatus(ProjectStatus.DELETED);
            projectRepository.save(project);
        }
    }

    @Override
    @Transactional
    public ProjectResponse restore(UUID projectId, UUID userId) {
        Project project = projectRepository.findById(projectId)
                .orElseThrow(() -> new ResourceNotFoundException("Project not found"));
        projectAccess.requireWorkspaceAndActive(project, userId);
        if (!userId.equals(project.getAdminId())) {
            throw new ForbiddenException("Only the admin can restore this project");
        }
        if (project.getDeletedAt() != null) {
            project.setDeletedAt(null);
            projectRepository.save(project);
        }
        return toResponse(project);
    }

    private Project find(UUID projectId) {
        Project project = projectRepository.findById(projectId)
                .orElseThrow(() -> new ResourceNotFoundException("Project not found"));
        if (project.getDeletedAt() != null) {
            throw new ResourceNotFoundException("Project not found");
        }
        return project;
    }
    private boolean isInvolved(Project p, UUID userId) {
        boolean leader = Stream.of(p.getCreatedBy(), p.getAdminId(), p.getProjectManagerId(), p.getTeamLeadId())
                .anyMatch(userId::equals);
        return leader || projectMemberRepository.existsByProjectIdAndUserId(p.getId(), userId);
    }

    private void validateProjectDueDates(Project project, LocalDate startDate, LocalDate deadline) {
        if (startDate != null && deadline != null && deadline.isBefore(startDate)) {
            throw new BadRequestException("Deadline cannot be before start date");
        }
        if (project.getId() == null
                || (Objects.equals(project.getStartDate(), startDate)
                && Objects.equals(project.getDeadline(), deadline))) {
            return;
        }
        for (var milestone : milestoneRepository.findByProjectIdOrderByDueDateAsc(project.getId())) {
            if (startDate != null && milestone.getDueDate().isBefore(startDate)) {
                throw new BadRequestException("Project start date cannot be after a milestone due date");
            }
            if (deadline != null && milestone.getDueDate().isAfter(deadline)) {
                throw new BadRequestException("Project deadline cannot be before a milestone due date");
            }
        }
    }

    private void apply(Project project, ProjectRequest request, UUID actorId, UUID workspaceId) {
        if (workspaceId == null) {
            throw new BadRequestException("Project must belong to a workspace");
        }
        Workspace workspace = workspaceRepository.findByIdForUpdate(workspaceId)
                .orElseThrow(() -> new ResourceNotFoundException("Workspace not found"));
        if (workspace.isPendingDeletion()) {
            throw new BadRequestException("Workspace is pending deletion");
        }
        User actor = userRepository.findById(actorId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));
        requireSameWorkspace(actor, workspaceId, "You");
        validateProjectDueDates(project, request.getStartDate(), request.getDeadline());
        ProjectStatus requestedStatus = request.getStatus() == null ? project.getStatus() : request.getStatus();
        LocalDate requestedCompleteDate = requestedStatus == ProjectStatus.COMPLETED
                ? (project.getCompleteDate() == null ? LocalDate.now() : project.getCompleteDate())
                : null;
        if (request.getStartDate() != null && requestedCompleteDate != null
                && requestedCompleteDate.isBefore(request.getStartDate())) {
            throw new BadRequestException("Project cannot be completed before its start date");
        }
        project.setName(request.getName().trim());
        project.setDescription(request.getDescription());
        project.setWorkspaceId(workspaceId);
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
        project.setProjectManagerId(findProjectManagerId(request.getProjectManagerEmail(), workspaceId));
        project.setTeamLeadId(findTeamLeadId(request.getTeamLeadEmail(), workspaceId));
        updateTeamSize(project);
    }

    private UUID findTeamLeadId(String email, UUID workspaceId) {
        if (email == null || email.isBlank()) {
            return null;
        }
        User teamLead = userRepository.findByEmailIgnoreCase(email.trim())
                .orElseThrow(() -> new BadRequestException("No user found with email: " + email));
        if (teamLead.getRole() != Role.TEAM_LEAD) {
            throw new BadRequestException("Selected user must have the TEAM_LEAD role");
        }
        if (!workspaceId.equals(teamLead.getWorkspaceId())) {
            throw new BadRequestException("Team lead must belong to the project's workspace");
        }
        return teamLead.getId();
    }
    @Override
    public List<MemberResponse> getMembers(UUID projectId, UUID userId) {
        Project project = find(projectId);
        projectAccess.requireWorkspaceAndActive(project, userId);
        if (!isInvolved(project, userId)) {
            throw new ForbiddenException("You don't have access to this project");
        }
        Map<UUID, MemberResponse> responses = new LinkedHashMap<>();
        addProjectUser(responses, project.getWorkspaceId(), project.getAdminId(), Role.ADMIN);
        addProjectUser(responses, project.getWorkspaceId(), project.getProjectManagerId(), Role.PROJECT_MANAGER);
        addProjectUser(responses, project.getWorkspaceId(), project.getTeamLeadId(), Role.TEAM_LEAD);
        for (ProjectMember member : projectMemberRepository.findByProjectId(projectId)) {
            User user = userRepository.findById(member.getUserId()).orElse(null);
            if (user != null && project.getWorkspaceId().equals(user.getWorkspaceId())
                    && !responses.containsKey(user.getId())) {
                responses.put(user.getId(), toMemberResponse(user, member.getRole()));
            }
        }
        return new ArrayList<>(responses.values());
    }

    @Override
    public InviteResponse inviteMember(UUID projectId, ProjectInviteRequest request, UUID userId) {
        return inviteService.createProjectInvite(projectId, request, userId);
    }

    @Override
    @Transactional
    public void removeMember(UUID projectId, UUID memberUserId, UUID userId) {
        Project project = find(projectId);
        projectAccess.requireWorkspaceAndActive(project, userId);
        if (!userId.equals(project.getAdminId())) {
            throw new ForbiddenException("Only the admin can remove members");
        }
        if (sprintRepository.existsByProjectIdAndAssignedContributorIdAndDeletedAtIsNull(projectId, memberUserId)) {
            throw new BadRequestException(
                    "Cannot remove a member while they are assigned to a sprint. Reassign the sprint first.");
        }
        if (memberUserId.equals(project.getProjectManagerId())) {
            project.setProjectManagerId(null);
        } else if (memberUserId.equals(project.getTeamLeadId())) {
            project.setTeamLeadId(null);
        } else {
            ProjectMember member = projectMemberRepository.findByProjectIdAndUserId(projectId, memberUserId)
                    .orElseThrow(() -> new ResourceNotFoundException("Member not found in this project"));
            sprintAssignmentRequestRepository.deletePendingForProjectMember(
                    projectId, memberUserId, SprintAssignmentRequestStatus.PENDING);
            projectMemberRepository.delete(member);
        }

        updateTeamSize(project);
        projectRepository.save(project);
    }

    private void addProjectUser(Map<UUID, MemberResponse> responses, UUID workspaceId, UUID userId, Role role) {
        if (userId == null) {
            return;
        }
        userRepository.findById(userId)
                .filter(user -> workspaceId.equals(user.getWorkspaceId()))
                .ifPresent(user -> responses.putIfAbsent(userId, toMemberResponse(user, role)));
    }

    private void clearIssueAssignments(UUID projectId, UUID memberUserId, UUID actorId) {
        List<UUID> issueIds = issueRepository.findByProjectIdAndDeletedAtIsNull(projectId).stream()
                .map(Issue::getId)
                .toList();
        if (issueIds.isEmpty()) {
            return;
        }
        List<IssueAssignee> assignments = issueAssigneeRepository.findByIssueIdIn(issueIds).stream()
                .filter(assignment -> memberUserId.equals(assignment.getUserId()))
                .toList();
        for (IssueAssignee assignment : assignments) {
            issueHistoryRecorder.record(assignment.getIssueId(), actorId,
                    IssueHistoryAction.ASSIGNEE_REMOVED, "assignee", memberUserId, null,
                    "Project member was removed");
        }
        issueAssigneeRepository.deleteAll(assignments);
    }

    private void rejectPendingIssueRequests(UUID projectId, UUID memberUserId, UUID actorId) {
        List<IssueAssignmentRequest> requests =
                issueAssignmentRequestRepository.findByProjectIdAndUserIdAndStatus(
                        projectId, memberUserId, AssignmentRequestStatus.PENDING);
        for (com.frietsync.backend.entity.issue.IssueAssignmentRequest request : requests) {
            request.setStatus(AssignmentRequestStatus.REJECTED);
            request.setDecidedBy(actorId);
            request.setDecidedAt(Instant.now());
            issueAssignmentRequestRepository.save(request);
            issueHistoryRecorder.record(request.getIssueId(), actorId,
                    IssueHistoryAction.ASSIGNMENT_REJECTED, "assignee", null, memberUserId,
                    "Project member was removed");
        }
    }

    private MemberResponse toMemberResponse(User user, Role role) {
        MemberResponse response = new MemberResponse();
        response.setUserId(user.getId());
        response.setName(user.getName());
        response.setEmail(user.getEmail());
        response.setRole(role);
        response.setProfilePictureUrl(profilePictureUrlService.getUrl(user));
        return response;
    }

    private void requireSameWorkspace(User user, UUID workspaceId, String subject) {
        if (!workspaceId.equals(user.getWorkspaceId())) {
            throw new BadRequestException(subject + " must belong to the project's workspace");
        }
    }

    private String uploadProfilePicture(UUID projectId, MultipartFile file) {
        if (bucketName == null || bucketName.isBlank()) {
            throw new StorageServiceException("Project profile picture storage is not configured");
        }
        if (file.isEmpty() || file.getSize() > 5L * 1024 * 1024) {
            throw new BadRequestException("Project picture must be non-empty and no larger than 5 MB");
        }

        virusScanService.scanFile(file);
        String originalName = file.getOriginalFilename();
        String safeName = originalName == null ? "profile-picture.png"
                : originalName.replaceAll("[^A-Za-z0-9._-]", "_");
        if (safeName.length() > 255) {
            safeName = safeName.substring(safeName.length() - 255);
        }
        String contentType = S3UploadValidator.validateProfilePicture(file, safeName);
        String extension = safeName.substring(safeName.lastIndexOf('.') + 1).toLowerCase();
        String key = "projects/" + projectId + "/profile/" + UUID.randomUUID() + "." + extension;
        try (var input = file.getInputStream()) {
            s3Client.putObject(PutObjectRequest.builder().bucket(bucketName).key(key)
                            .contentType(contentType).build(),
                    RequestBody.fromInputStream(input, file.getSize()));
        } catch (IOException | SdkClientException | S3Exception exception) {
            throw new StorageServiceException("Could not upload project profile picture", exception);
        }
        deleteS3ObjectOnRollback(key);
        return key;
    }

    private void enqueueStoredFileDeletion(String key) {
        if (key != null && !key.isBlank()) cleanupService.enqueue(bucketName, key);
    }

    private void deleteS3ObjectOnRollback(String key) {
        if (!TransactionSynchronizationManager.isSynchronizationActive()) return;
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCompletion(int status) {
                if (status != STATUS_COMMITTED) cleanupService.enqueue(bucketName, key);
            }
        });
    }

    private void updateTeamSize(Project project) {
        Set<UUID> people = new HashSet<>();
        people.add(project.getAdminId());
        if (project.getProjectManagerId() != null) {
            people.add(project.getProjectManagerId());
        }
        if (project.getTeamLeadId() != null) {
            people.add(project.getTeamLeadId());
        }
        if (project.getId() != null) {
            for (ProjectMember member : projectMemberRepository.findByProjectId(project.getId())) {
                people.add(member.getUserId());
            }
        }
        project.setTeamSize(people.size());
    }
}
