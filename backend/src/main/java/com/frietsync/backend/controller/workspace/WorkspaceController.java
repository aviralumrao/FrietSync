package com.frietsync.backend.controller.workspace;

import com.frietsync.backend.dto.workspace.CreateWorkspaceRequest;
import com.frietsync.backend.dto.workspace.UpdateWorkspaceRequest;
import com.frietsync.backend.dto.workspace.WorkspaceResponse;
import com.frietsync.backend.dto.project.MemberResponse;
import com.frietsync.backend.service.workspace.WorkspaceService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/workspaces")
@RequiredArgsConstructor
public class WorkspaceController {

    private final WorkspaceService workspaceService;

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public WorkspaceResponse createWorkspace(
            @AuthenticationPrincipal UUID userId,
            @Valid @RequestBody CreateWorkspaceRequest request
    ) {
        return workspaceService.createWorkspace(userId, request);
    }

    @GetMapping("/me")
    public WorkspaceResponse getMyWorkspace(@AuthenticationPrincipal UUID userId) {
        return workspaceService.getMyWorkspace(userId);
    }

    @GetMapping("/{workspaceId}")
    public WorkspaceResponse getWorkspace(
            @AuthenticationPrincipal UUID userId,
            @PathVariable UUID workspaceId
    ) {
        return workspaceService.getWorkspace(userId, workspaceId);
    }

    @PatchMapping("/{workspaceId}")
    public WorkspaceResponse updateWorkspace(
            @AuthenticationPrincipal UUID userId,
            @PathVariable UUID workspaceId,
            @Valid @RequestBody UpdateWorkspaceRequest request
    ) {
        return workspaceService.updateWorkspace(userId, workspaceId, request);
    }

    @GetMapping("/{workspaceId}/members")
    public List<MemberResponse> getWorkspaceMembers(
            @AuthenticationPrincipal UUID userId,
            @PathVariable UUID workspaceId
    ) {
        return workspaceService.getWorkspaceMembers(userId, workspaceId);
    }
}