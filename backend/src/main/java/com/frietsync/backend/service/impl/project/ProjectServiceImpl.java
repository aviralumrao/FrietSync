package com.frietsync.backend.service.impl.project;

import com.frietsync.backend.dto.project.*;
import com.frietsync.backend.entity.project.Label;
import com.frietsync.backend.entity.project.Project;
import com.frietsync.backend.entity.project.ProjectMember;
import com.frietsync.backend.entity.project.ProjectStatus;
import com.frietsync.backend.entity.user.Role;
import com.frietsync.backend.entity.user.User;
import com.frietsync.backend.exception.BadRequestException;
import com.frietsync.backend.exception.ForbiddenException;
import com.frietsync.backend.exception.ResourceNotFoundException;
import com.frietsync.backend.repository.project.ProjectMemberRepository;
import com.frietsync.backend.repository.project.ProjectRepository;
import com.frietsync.backend.repository.project.LabelRepository;
import com.frietsync.backend.repository.user.UserRepository;
import com.frietsync.backend.service.project.ProjectService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import java.time.LocalDate;
import java.util.*;
import java.util.stream.Stream;

@Service
@RequiredArgsConstructor
public class ProjectServiceImpl implements ProjectService {

    private final ProjectRepository projectRepository;
    private final UserRepository userRepository;
    private final ProjectMemberRepository projectMemberRepository;
    private final LabelRepository labelRepository;

    @Override
    public ProjectResponse create(ProjectRequest request, UUID userId) {
        Project project = new Project();
        project.setCreatedBy(userId);
        project.setAdminId(userId);
        apply(project, request);
        return ProjectResponse.fromEntity(projectRepository.save(project));
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
    public void delete(UUID projectId, UUID userId) {
        Project project = find(projectId);
        if (!userId.equals(project.getAdminId())) {
            throw new ForbiddenException("Only the admin can delete this project");
        }
        projectMemberRepository.deleteAll(projectMemberRepository.findByProjectId(projectId));
        labelRepository.deleteAll(labelRepository.findByProjectIdOrderByNameAsc(projectId));
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
        project.setProjectManagerId(findUserId(request.getProjectManagerEmail()));
        project.setTeamLeadId(findUserId(request.getTeamLeadEmail()));
        updateTeamSize(project);
    }
    private UUID findUserId(String email) {
        if (email == null || email.isBlank()) {
            return null;
        }
        return userRepository.findByEmail(email.trim())
                .map(User::getId)
                .orElseThrow(() -> new BadRequestException("No user found with email: " + email));
    }
    @Override
    public List<MemberResponse> getMembers(UUID projectId, UUID userId) {
        Project project = find(projectId);
        if (!isInvolved(project, userId)) {
            throw new ForbiddenException("You don't have access to this project");
        }
        List<MemberResponse> responses = new ArrayList<>();
        for (ProjectMember member : projectMemberRepository.findByProjectId(projectId)) {
            User user = userRepository.findById(member.getUserId()).orElse(null);
            if (user != null) {
                responses.add(toMemberResponse(user, member.getRole()));
            }
        }
        return responses;
    }

    @Override
    public MemberResponse addMember(UUID projectId, MemberRequest request, UUID userId) {
        Project project = find(projectId);
        if (!userId.equals(project.getAdminId())) {
            throw new ForbiddenException("Only the admin can add members");
        }
        if (request.getRole() == Role.ADMIN) {
            throw new BadRequestException("Project role cannot be ADMIN");
        }
        User user = userRepository.findByEmail(request.getEmail().trim().toLowerCase())
                .orElseThrow(() -> new BadRequestException("No user found with email: " + request.getEmail()));
        if (projectMemberRepository.existsByProjectIdAndUserId(projectId, user.getId())) {
            throw new BadRequestException("User is already a member of this project");
        }

        ProjectMember member = new ProjectMember();
        member.setProjectId(projectId);
        member.setUserId(user.getId());
        member.setRole(request.getRole());
        projectMemberRepository.save(member);
        applyLeaderRole(project, user.getId(), request.getRole());
        updateTeamSize(project);
        projectRepository.save(project);
        return toMemberResponse(user, member.getRole());
    }

    @Override
    public void removeMember(UUID projectId, UUID memberUserId, UUID userId) {
        Project project = find(projectId);
        if (!userId.equals(project.getAdminId())) {
            throw new ForbiddenException("Only the admin can remove members");
        }
        ProjectMember member = projectMemberRepository.findByProjectIdAndUserId(projectId, memberUserId)
                .orElseThrow(() -> new ResourceNotFoundException("Member not found in this project"));
        projectMemberRepository.delete(member);
        applyLeaderRole(project, memberUserId, Role.CONTRIBUTOR);

        updateTeamSize(project);
        projectRepository.save(project);
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
    @Override
    public MemberResponse assignRole(UUID projectId, MemberRequest request, UUID userId) {
        Project project = find(projectId);
        boolean isAdmin = userId.equals(project.getAdminId());
        boolean isPm = userId.equals(project.getProjectManagerId());
        boolean isLead = userId.equals(project.getTeamLeadId());
        if (!isAdmin && !isPm && !isLead) {
            throw new ForbiddenException("Only the admin, project manager or team lead can assign roles");
        }
        if (request.getRole() == Role.ADMIN) {
            throw new BadRequestException("Project role cannot be ADMIN");
        }
        if (!isAdmin && request.getRole() == Role.PROJECT_MANAGER) {
            throw new ForbiddenException("Only the admin can assign the project manager");
        }
        if (!isAdmin && !isPm && request.getRole() == Role.TEAM_LEAD) {
            throw new ForbiddenException("Only the admin or project manager can assign the team lead");
        }

        User user = userRepository.findByEmail(request.getEmail().trim().toLowerCase())
                .orElseThrow(() -> new BadRequestException("No user found with email: " + request.getEmail()));
        ProjectMember member = projectMemberRepository.findByProjectIdAndUserId(projectId, user.getId())
                .orElseThrow(() -> new ResourceNotFoundException("User is not a member of this project"));

        if (!isAdmin && member.getRole() == Role.PROJECT_MANAGER) {
            throw new ForbiddenException("Only the admin can change the project manager's role");
        }
        if (!isAdmin && !isPm && member.getRole() == Role.TEAM_LEAD) {
            throw new ForbiddenException("Only the admin or project manager can change the team lead's role");
        }

        member.setRole(request.getRole());
        projectMemberRepository.save(member);

        applyLeaderRole(project, user.getId(), request.getRole());
        updateTeamSize(project);
        projectRepository.save(project);
        return toMemberResponse(user, member.getRole());
    }

    @Override
    public LabelResponse createLabel(UUID projectId, LabelRequest request, UUID userId) {
        Project project = find(projectId);
        boolean canCreate = userId.equals(project.getAdminId())
                || userId.equals(project.getProjectManagerId());
        if (!canCreate) {
            throw new ForbiddenException("Only the admin or project manager can create labels");
        }

        String name = request.getName().trim();
        if (labelRepository.existsByProjectIdAndNameIgnoreCase(projectId, name)) {
            throw new BadRequestException("Label already exists in this project");
        }

        Label label = new Label();
        label.setProjectId(projectId);
        label.setName(name);
        label.setColor(request.getColor());
        labelRepository.save(label);
        return toLabelResponse(label);
    }

    @Override
    public List<LabelResponse> getLabels(UUID projectId, UUID userId) {
        Project project = find(projectId);
        if (!isInvolved(project, userId)) {
            throw new ForbiddenException("You don't have access to this project");
        }
        List<LabelResponse> responses = new ArrayList<>();
        for (Label label : labelRepository.findByProjectIdOrderByNameAsc(projectId)) {
            responses.add(toLabelResponse(label));
        }
        return responses;
    }

    private LabelResponse toLabelResponse(Label label) {
        LabelResponse response = new LabelResponse();
        response.setId(label.getId());
        response.setName(label.getName());
        response.setColor(label.getColor());
        return response;
    }

    private void applyLeaderRole(Project project, UUID memberUserId, Role role) {
        if (memberUserId.equals(project.getProjectManagerId()) && role != Role.PROJECT_MANAGER) {
            project.setProjectManagerId(null);
        }
        if (memberUserId.equals(project.getTeamLeadId()) && role != Role.TEAM_LEAD) {
            project.setTeamLeadId(null);
        }
        if (role == Role.PROJECT_MANAGER) {
            project.setProjectManagerId(memberUserId);
        }
        if (role == Role.TEAM_LEAD) {
            project.setTeamLeadId(memberUserId);
        }
    }
}