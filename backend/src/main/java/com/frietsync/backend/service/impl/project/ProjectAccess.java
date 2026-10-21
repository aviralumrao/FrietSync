package com.frietsync.backend.service.impl.project;

import com.frietsync.backend.entity.issue.Issue;
import com.frietsync.backend.entity.project.Project;
import com.frietsync.backend.entity.project.ProjectMember;
import com.frietsync.backend.entity.user.Role;
import com.frietsync.backend.entity.user.User;
import com.frietsync.backend.entity.workspace.Workspace;
import com.frietsync.backend.exception.ForbiddenException;
import com.frietsync.backend.exception.ResourceNotFoundException;
import com.frietsync.backend.repository.issue.IssueRepository;
import com.frietsync.backend.repository.project.ProjectMemberRepository;
import com.frietsync.backend.repository.project.ProjectRepository;
import com.frietsync.backend.repository.user.UserRepository;
import com.frietsync.backend.repository.workspace.WorkspaceRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.UUID;

@Component
@RequiredArgsConstructor
public class ProjectAccess {

    private final ProjectRepository projectRepository;
    private final ProjectMemberRepository projectMemberRepository;
    private final IssueRepository issueRepository;
    private final UserRepository userRepository;
    private final WorkspaceRepository workspaceRepository;

    public Project project(UUID projectId) {
        return projectRepository.findById(projectId)
                .orElseThrow(() -> new ResourceNotFoundException("Project not found"));
    }

    public Issue issue(UUID issueId) {
        Issue issue = issueRepository.findById(issueId)
                .orElseThrow(() -> new ResourceNotFoundException("Issue not found"));
        if (issue.getDeletedAt() != null) {
            throw new ResourceNotFoundException("Issue not found");
        }
        return issue;
    }

    public Role roleOf(Project project, UUID userId) {
        if (userId.equals(project.getAdminId())) {
            return Role.ADMIN;
        }
        if (userId.equals(project.getProjectManagerId())) {
            return Role.PROJECT_MANAGER;
        }
        if (userId.equals(project.getTeamLeadId())) {
            return Role.TEAM_LEAD;
        }
        return projectMemberRepository.findByProjectIdAndUserId(project.getId(), userId)
                .map(ProjectMember::getRole)
                .orElse(null);
    }

    public Role requireMember(Project project, UUID userId) {
        requireWorkspaceAndActive(project, userId);
        Role role = roleOf(project, userId);
        if (role == null) {
            throw new ForbiddenException("You don't have access to this project");
        }
        return role;
    }

    public void requireWorkspaceAndActive(Project project, UUID userId) {
        if (userId == null) {
            throw new ForbiddenException("Authentication is required");
        }
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));
        if (project.getWorkspaceId() == null
                || user.getWorkspaceId() == null
                || !project.getWorkspaceId().equals(user.getWorkspaceId())) {
            throw new ForbiddenException("You must belong to the project's workspace");
        }
        Workspace workspace = workspaceRepository.findById(project.getWorkspaceId())
                .orElseThrow(() -> new ResourceNotFoundException("Workspace not found"));
        if (workspace.isPendingDeletion()) {
            throw new ResourceNotFoundException("Workspace not found");
        }
    }

    public UUID requireActiveWorkspace(UUID userId) {
        if (userId == null) {
            throw new ForbiddenException("Authentication is required");
        }
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));
        if (user.getWorkspaceId() == null) {
            throw new ForbiddenException("You must belong to an active workspace");
        }
        Workspace workspace = workspaceRepository.findById(user.getWorkspaceId())
                .orElseThrow(() -> new ResourceNotFoundException("Workspace not found"));
        if (workspace.isPendingDeletion()) {
            throw new ResourceNotFoundException("Workspace not found");
        }
        return workspace.getId();
    }

    public Role require(Project project, UUID userId, Role... allowed) {
        Role role = requireMember(project, userId);
        if (role == Role.ADMIN) {
            return role;
        }
        for (Role candidate : allowed) {
            if (candidate == role) {
                return role;
            }
        }
        throw new ForbiddenException("You don't have permission to do this");
    }
}
