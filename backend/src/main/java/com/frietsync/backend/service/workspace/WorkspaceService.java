package com.frietsync.backend.service.workspace;

import com.frietsync.backend.dto.workspace.CreateWorkspaceRequest;
import com.frietsync.backend.dto.workspace.WorkspaceResponse;
import com.frietsync.backend.dto.workspace.UpdateWorkspaceRequest;
import com.frietsync.backend.dto.project.MemberResponse;

import java.util.List;
import java.util.UUID;

public interface WorkspaceService {

    WorkspaceResponse createWorkspace(
            UUID userId,
            CreateWorkspaceRequest request
    );

    WorkspaceResponse getMyWorkspace(UUID userId);

    WorkspaceResponse getWorkspace(UUID userId, UUID workspaceId);

    WorkspaceResponse updateWorkspace(
            UUID userId,
            UUID workspaceId,
            UpdateWorkspaceRequest request
    );

    List<MemberResponse> getWorkspaceMembers(UUID userId, UUID workspaceId);
}