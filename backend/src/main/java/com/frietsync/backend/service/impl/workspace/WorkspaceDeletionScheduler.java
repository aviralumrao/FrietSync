package com.frietsync.backend.service.impl.workspace;

import com.frietsync.backend.service.workspace.WorkspaceService;
import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class WorkspaceDeletionScheduler {
    private final WorkspaceService workspaceService;

    @Scheduled(fixedDelayString = "${app.workspace.deletion-check-delay-ms:300000}")
    public void purgeExpiredWorkspaces() {
        workspaceService.purgeExpiredWorkspaces();
    }
}
