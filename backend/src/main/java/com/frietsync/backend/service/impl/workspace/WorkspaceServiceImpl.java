package com.frietsync.backend.service.impl.workspace;

import com.frietsync.backend.dto.workspace.CreateWorkspaceRequest;
import com.frietsync.backend.dto.workspace.UpdateWorkspaceRequest;
import com.frietsync.backend.dto.workspace.WorkspaceResponse;
import com.frietsync.backend.dto.project.MemberResponse;
import com.frietsync.backend.entity.user.Role;
import com.frietsync.backend.entity.user.User;
import com.frietsync.backend.entity.workspace.Workspace;
import com.frietsync.backend.exception.BadRequestException;
import com.frietsync.backend.exception.ForbiddenException;
import com.frietsync.backend.exception.ResourceNotFoundException;
import com.frietsync.backend.repository.user.UserRepository;
import com.frietsync.backend.repository.workspace.WorkspaceRepository;
import com.frietsync.backend.service.workspace.WorkspaceService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class WorkspaceServiceImpl implements WorkspaceService {

    private final UserRepository userRepository;
    private final WorkspaceRepository workspaceRepository;

    @Override
    @Transactional
    public WorkspaceResponse createWorkspace(
            UUID userId,
            CreateWorkspaceRequest request
    ) {
        User user = userRepository.findByIdForUpdate(userId)
                .orElseThrow(() -> new BadRequestException("User not found"));

        if (user.getRole() != Role.ADMIN) {
            throw new BadRequestException(
                    "Only admins can create a workspace"
            );
        }

        if (user.getWorkspaceId() != null) {
            throw new BadRequestException(
                    "User already belongs to a workspace"
            );
        }

        Workspace workspace = new Workspace();
        workspace.setName(request.getName().trim());
        workspace.setAdminId(userId);

        Workspace savedWorkspace = workspaceRepository.save(workspace);

        user.setWorkspaceId(savedWorkspace.getId());
        userRepository.save(user);

        return toResponse(savedWorkspace);
    }

    @Override
    @Transactional(readOnly = true)
    public WorkspaceResponse getMyWorkspace(UUID userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));
        if (user.getWorkspaceId() == null) {
            throw new BadRequestException("User does not belong to a workspace");
        }
        return toResponse(workspaceRepository.findById(user.getWorkspaceId())
                .orElseThrow(() -> new ResourceNotFoundException("Workspace not found")));
    }

    @Override
    @Transactional(readOnly = true)
    public WorkspaceResponse getWorkspace(UUID userId, UUID workspaceId) {
        requireWorkspaceMember(userId, workspaceId);
        return toResponse(workspaceRepository.findById(workspaceId)
                .orElseThrow(() -> new ResourceNotFoundException("Workspace not found")));
    }

    @Override
    @Transactional
    public WorkspaceResponse updateWorkspace(
            UUID userId,
            UUID workspaceId,
            UpdateWorkspaceRequest request
    ) {
        Workspace workspace = workspaceRepository.findByIdForUpdate(workspaceId)
                .orElseThrow(() -> new ResourceNotFoundException("Workspace not found"));
        if (!workspace.getAdminId().equals(userId)) {
            throw new ForbiddenException("Only the workspace owner can update its name");
        }
        workspace.setName(request.getName().trim());
        return toResponse(workspaceRepository.save(workspace));
    }

    @Override
    @Transactional(readOnly = true)
    public List<MemberResponse> getWorkspaceMembers(UUID userId, UUID workspaceId) {
        requireWorkspaceMember(userId, workspaceId);
        return userRepository.findAllByWorkspaceIdOrderByCreatedAtAsc(workspaceId).stream()
                .map(this::toMemberResponse)
                .toList();
    }

    private void requireWorkspaceMember(UUID userId, UUID workspaceId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));
        if (!workspaceId.equals(user.getWorkspaceId())) {
            throw new ForbiddenException("You do not belong to this workspace");
        }
    }

    private MemberResponse toMemberResponse(User user) {
        MemberResponse response = new MemberResponse();
        response.setUserId(user.getId());
        response.setName(user.getName());
        response.setEmail(user.getEmail());
        response.setRole(user.getRole());
        return response;
    }

    private WorkspaceResponse toResponse(Workspace workspace) {
        return new WorkspaceResponse(
                workspace.getId(),
                workspace.getName(),
                workspace.getAdminId(),
                workspace.getCreatedOn()
        );
    }
}