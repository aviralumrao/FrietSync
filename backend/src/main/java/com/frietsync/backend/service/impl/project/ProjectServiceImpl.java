package com.frietsync.backend.service.impl.project;

import com.frietsync.backend.dto.project.MemberRequest;
import com.frietsync.backend.dto.project.MemberResponse;
import com.frietsync.backend.dto.project.ProjectRequest;
import com.frietsync.backend.dto.project.ProjectResponse;
import com.frietsync.backend.entity.project.Project;
import com.frietsync.backend.entity.project.ProjectMember;
import com.frietsync.backend.entity.project.ProjectStatus;
import com.frietsync.backend.entity.sprint.SprintAssignmentRequestStatus;
import com.frietsync.backend.entity.user.Role;
import com.frietsync.backend.entity.user.User;
import com.frietsync.backend.exception.BadRequestException;
import com.frietsync.backend.exception.ForbiddenException;
import com.frietsync.backend.exception.ResourceNotFoundException;
import com.frietsync.backend.repository.project.ProjectMemberRepository;
import com.frietsync.backend.repository.project.ProjectRepository;
import com.frietsync.backend.repository.sprint.SprintAssignmentRequestRepository;
import com.frietsync.backend.repository.sprint.SprintRepository;
import com.frietsync.backend.repository.user.UserRepository;
import com.frietsync.backend.service.project.ProjectService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.frietsync.backend.entity.project.ProjectRole;

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

    @Override
    @Transactional
    public ProjectResponse create(ProjectRequest request, UUID userId) {
        User admin = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));

        Project project = new Project();
        project.setCreatedBy(userId);
        project.setAdminId(userId);
        apply(project, request);
        validateSameWorkspace(admin, project);

        return ProjectResponse.fromEntity(projectRepository.save(project));
    }
    private void validateSameWorkspace(User user, Project project) {
        if (project.getWorkspaceId() == null || user.getWorkspaceId() == null
                || !project.getWorkspaceId().equals(user.getWorkspaceId())) {
            throw new BadRequestException(
                    "User " + user.getEmail() + " does not belong to this project's workspace");
        }
    }
    private UUID findProjectManagerId(String email, Project project) {
        if (email == null || email.isBlank()) {
            throw new BadRequestException("Project manager email is required");
        }
        User manager = userRepository.findByEmail(email.trim().toLowerCase())
                .orElseThrow(() -> new BadRequestException("No user found with email: " + email));
        if (manager.getRole() != Role.PROJECT_MANAGER) {
            throw new BadRequestException("Selected user must have the PROJECT_MANAGER role");
        }
        validateSameWorkspace(manager, project);
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

    @Override
    public ProjectResponse update(UUID projectId, ProjectRequest request, UUID userId) {
        Project project = find(projectId);
        boolean canEdit = userId.equals(project.getAdminId())
                || userId.equals(project.getProjectManagerId());
        if (!canEdit) {
            throw new ForbiddenException("Only the admin or project manager can edit this project");
        }
        apply(project, request);
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
    private void apply(Project project, ProjectRequest request) {
        if (request.getStartDate() != null && request.getDeadline() != null
                && request.getDeadline().isBefore(request.getStartDate())) {
            throw new BadRequestException("Deadline cannot be before start date");
        }
        project.setName(request.getName().trim());
        project.setDescription(request.getDescription());
        project.setWorkspaceId(request.getWorkspaceId());
        project.setStartDate(request.getStartDate());
        project.setDeadline(request.getDeadline());
        project.setProjectManagerId(findProjectManagerId(request.getProjectManagerEmail(), project));
        project.setTeamLeadId(findTeamLeadId(request.getTeamLeadEmail(), project));
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

        updateTeamSize(project);
    }
    private UUID findTeamLeadId(String email, Project project) {
        if (email == null || email.isBlank()) {
            return null;
        }
        User teamLead = userRepository.findByEmail(email.trim().toLowerCase())
                .orElseThrow(() -> new BadRequestException("No user found with email: " + email));
        if (teamLead.getRole() != Role.TEAM_LEAD) {
            throw new BadRequestException("Selected user must have the TEAM_LEAD role");
        }
        validateSameWorkspace(teamLead, project);
        return teamLead.getId();
    }
    @Override
    public List<MemberResponse> getMembers(UUID projectId, UUID userId) {
        Project project = find(projectId);
        if (!isInvolved(project, userId)) {
            throw new ForbiddenException("You don't have access to this project");
        }
        List<MemberResponse> responses = new ArrayList<>();
        for (ProjectMember member : projectMemberRepository.findByProjectId(projectId)) {
            userRepository.findById(member.getUserId())
                    .ifPresent(user -> responses.add(toMemberResponse(user, member.getProjectRole())));
        }
        return responses;
    }

    @Override
    @Transactional
    public MemberResponse addMember(UUID projectId, MemberRequest request, UUID userId) {
        Project project = find(projectId);
        if (!userId.equals(project.getAdminId())) {
            throw new ForbiddenException("Only the admin can add members");
        }

        ProjectRole role = request.getRole();
        if (role == ProjectRole.ADMIN
                || role == ProjectRole.PROJECT_MANAGER
                || role == ProjectRole.TEAM_LEAD) {
            throw new BadRequestException(
                    "Role " + role + " is set in the project settings, not when adding a member");
        }

        User user = userRepository.findByEmail(request.getEmail().trim().toLowerCase())
                .orElseThrow(() -> new BadRequestException("No user found with email: " + request.getEmail()));

        if (user.getId().equals(project.getAdminId())) {
            throw new BadRequestException("The project admin cannot invite themselves to the project");
        }

        if (user.getId().equals(project.getProjectManagerId())
                || user.getId().equals(project.getTeamLeadId())) {
            throw new BadRequestException("User is already the project manager or team lead of this project");
        }

        validateSameWorkspace(user, project);

        if (projectMemberRepository.existsByProjectIdAndUserId(projectId, user.getId())) {
            throw new BadRequestException("User is already a member of this project");
        }

        ProjectMember member = new ProjectMember();
        member.setProjectId(projectId);
        member.setUserId(user.getId());
        member.setProjectRole(role);
        projectMemberRepository.save(member);

        updateTeamSize(project);
        projectRepository.save(project);
        return toMemberResponse(user, role);
    }

    @Override
    @Transactional
    public void removeMember(UUID projectId, UUID memberUserId, UUID userId) {
        Project project = find(projectId);
        if (!userId.equals(project.getAdminId())) {
            throw new ForbiddenException("Only the admin can remove members");
        }
        ProjectMember member = projectMemberRepository.findByProjectIdAndUserId(projectId, memberUserId)
                .orElseThrow(() -> new ResourceNotFoundException("Member not found in this project"));
        if (sprintRepository.existsByProjectIdAndAssignedContributorId(projectId, memberUserId)) {
            throw new BadRequestException(
                    "Cannot remove a member while they are assigned to a sprint. Reassign the sprint first.");
        }
        sprintAssignmentRequestRepository.deletePendingForProjectMember(
                projectId, memberUserId, SprintAssignmentRequestStatus.PENDING);
        projectMemberRepository.delete(member);

        updateTeamSize(project);
        projectRepository.save(project);
    }

    private MemberResponse toMemberResponse(User user, ProjectRole role) {
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