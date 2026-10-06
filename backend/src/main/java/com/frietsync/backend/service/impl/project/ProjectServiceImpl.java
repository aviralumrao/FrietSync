package com.frietsync.backend.service.impl.project;

import com.frietsync.backend.dto.project.MemberRequest;
import com.frietsync.backend.dto.project.MemberResponse;
import com.frietsync.backend.dto.project.ProjectRequest;
import com.frietsync.backend.dto.project.ProjectResponse;
import com.frietsync.backend.entity.project.Project;
import com.frietsync.backend.entity.project.ProjectMember;
import com.frietsync.backend.entity.project.ProjectStatus;
import com.frietsync.backend.entity.sprint.Sprint;
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
}