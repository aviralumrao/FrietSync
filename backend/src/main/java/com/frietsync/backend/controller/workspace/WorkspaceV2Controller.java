package com.frietsync.backend.controller.workspace;

import com.frietsync.backend.dto.project.MemberResponse;
import com.frietsync.backend.dto.workspace.UpdateWorkspaceRequest;
import com.frietsync.backend.dto.workspace.WorkspaceResponse;
import com.frietsync.backend.service.workspace.WorkspaceService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v2/workspaces/me")
@RequiredArgsConstructor
public class WorkspaceV2Controller {

    private final WorkspaceService workspaceService;

    @PatchMapping
    public WorkspaceResponse updateMyWorkspace(
            @AuthenticationPrincipal UUID userId,
            @Valid @RequestBody UpdateWorkspaceRequest request
    ) {
        return workspaceService.updateMyWorkspace(userId, request);
    }

    @DeleteMapping
    @ResponseStatus(HttpStatus.ACCEPTED)
    public WorkspaceResponse deleteMyWorkspace(@AuthenticationPrincipal UUID userId) {
        return workspaceService.deleteMyWorkspace(userId);
    }

    @GetMapping("/members")
    public List<MemberResponse> getMyWorkspaceMembers(@AuthenticationPrincipal UUID userId) {
        return workspaceService.getMyWorkspaceMembers(userId);
    }
}
