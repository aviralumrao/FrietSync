package com.frietsync.backend.service.impl.project;

import com.frietsync.backend.dto.project.MemberResponse;
import com.frietsync.backend.dto.project.ProjectInviteRequest;
import com.frietsync.backend.dto.invite.InviteResponse;
import com.frietsync.backend.dto.project.ProjectRequest;
import com.frietsync.backend.dto.project.ProjectResponse;
import com.frietsync.backend.entity.project.Project;
import com.frietsync.backend.entity.project.ProjectMember;
import com.frietsync.backend.entity.project.ProjectStatus;
import com.frietsync.backend.entity.sprint.SprintAssignmentRequestStatus;
import com.frietsync.backend.entity.user.Role;
import com.frietsync.backend.entity.user.User;
import com.frietsync.backend.entity.workspace.Workspace;
import com.frietsync.backend.exception.BadRequestException;
import com.frietsync.backend.exception.ForbiddenException;
import com.frietsync.backend.exception.ResourceNotFoundException;
import com.frietsync.backend.repository.project.ProjectMemberRepository;
import com.frietsync.backend.repository.project.ProjectRepository;
import com.frietsync.backend.repository.sprint.SprintAssignmentRequestRepository;
import com.frietsync.backend.repository.sprint.SprintRepository;
import com.frietsync.backend.repository.user.UserRepository;
import com.frietsync.backend.repository.workspace.WorkspaceRepository;
import com.frietsync.backend.service.project.ProjectService;
import com.frietsync.backend.service.invite.InviteService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.*;
import java.util.stream.Stream;

@Service
@RequiredArgsConstructor
public class ProjectServiceImpl implements ProjectService {

    private final ProjectRepository projectRepository;
    private final UserRepository userRepository;
    private final ProjectMemberRepository projectMemberRepository;
    private final SprintRepository sprintRepository;
    private final SprintAssignmentRequestRepository sprintAssignmentRequestRepository;
    private final WorkspaceRepository workspaceRepository;
    private final InviteService inviteService;

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
        if (!userId.equals(workspace.getAdminId())) {
            throw new ForbiddenException("Only the workspace admin can create projects");
        }
        Project project = new Project();
        project.setCreatedBy(userId);
        project.setWorkspaceId(workspace.getId());
        project.setAdminId(workspace.getAdminId());
        apply(project, request, workspace.getId());
        return ProjectResponse.fromEntity(projectRepository.save(project));
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

    @Transactional
    public ProjectResponse update(UUID projectId, ProjectRequest request, UUID userId) {
        Project project = projectRepository.findByIdForUpdate(projectId)
                .orElseThrow(() -> new ResourceNotFoundException("Project not found"));
        boolean canEdit = userId.equals(project.getAdminId())
                || userId.equals(project.getProjectManagerId());
        if (!canEdit) {
            throw new ForbiddenException("Only the admin or project manager can edit this project");
        }
        apply(project, request, project.getWorkspaceId());
        return ProjectResponse.fromEntity(projectRepository.save(project));
    }

    @Override
    @Transactional
    public void delete(UUID projectId, UUID userId) {
        Project project = find(projectId);
        if (!userId.equals(project.getAdminId())) {
            throw new ForbiddenException("Only the admin can delete this project");
        }
        sprintAssignmentRequestRepository.deleteAllForProject(projectId);
        sprintRepository.deleteByProjectId(projectId);
        projectMemberRepository.deleteAll(projectMemberRepository.findByProjectId(projectId));
        projectRepository.delete(project);
    }

    private Project find(UUID projectId) {
        return projectRepository.findById(projectId)
                .orElseThrow(() -> new ResourceNotFoundException("Project not found"));
    }
    private boolean isInvolved(Project p, UUID userId) {
        boolean leader = Stream.of(p.getCreatedBy(), p.getAdminId(), p.getProjectManagerId(), p.getTeamLeadId())
                .anyMatch(userId::equals);
        return leader || projectMemberRepository.existsByProjectIdAndUserId(p.getId(), userId);
    }
    private void apply(Project project, ProjectRequest request, UUID workspaceId) {
        if (request.getStartDate() != null && request.getDeadline() != null
                && request.getDeadline().isBefore(request.getStartDate())) {
            throw new BadRequestException("Deadline cannot be before start date");
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
        if (!isInvolved(project, userId)) {
            throw new ForbiddenException("You don't have access to this project");
        }
        Map<UUID, MemberResponse> responses = new LinkedHashMap<>();
        addProjectUser(responses, project.getAdminId(), Role.ADMIN);
        addProjectUser(responses, project.getProjectManagerId(), Role.PROJECT_MANAGER);
        addProjectUser(responses, project.getTeamLeadId(), Role.TEAM_LEAD);
        for (ProjectMember member : projectMemberRepository.findByProjectId(projectId)) {
            User user = userRepository.findById(member.getUserId()).orElse(null);
            if (user != null && !responses.containsKey(user.getId())) {
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
        if (!userId.equals(project.getAdminId())) {
            throw new ForbiddenException("Only the admin can remove members");
        }
        if (sprintRepository.existsByProjectIdAndAssignedContributorId(projectId, memberUserId)) {
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

    private void addProjectUser(Map<UUID, MemberResponse> responses, UUID userId, Role role) {
        if (userId == null) {
            return;
        }
        userRepository.findById(userId)
                .ifPresent(user -> responses.putIfAbsent(userId, toMemberResponse(user, role)));
    }

    private MemberResponse toMemberResponse(User user, Role role) {
        MemberResponse response = new MemberResponse();
        response.setUserId(user.getId());
        response.setName(user.getName());
        response.setEmail(user.getEmail());
        response.setRole(role);
        return response;
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