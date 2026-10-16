package com.frietsync.backend.controller.dashboard;

import com.frietsync.backend.dto.dashboard.ProjectBoardResponse;
import com.frietsync.backend.dto.dashboard.ProjectCalendarResponse;
import com.frietsync.backend.dto.dashboard.ProjectTimelineResponse;
import com.frietsync.backend.service.impl.dashboard.ProjectDashboardService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/projects/{id}")
@RequiredArgsConstructor
public class ProjectDashboardController {

    private final ProjectDashboardService dashboardService;

    @GetMapping("/board")
    public ResponseEntity<ProjectBoardResponse> board(
            @PathVariable UUID id,
            @AuthenticationPrincipal UUID userId) {
        return ResponseEntity.ok(dashboardService.board(id, userId));
    }

    @GetMapping("/calendar")
    public ResponseEntity<ProjectCalendarResponse> calendar(
            @PathVariable UUID id,
            @AuthenticationPrincipal UUID userId) {
        return ResponseEntity.ok(dashboardService.calendar(id, userId));
    }

    @GetMapping("/timeline")
    public ResponseEntity<ProjectTimelineResponse> timeline(
            @PathVariable UUID id,
            @AuthenticationPrincipal UUID userId) {
        return ResponseEntity.ok(dashboardService.timeline(id, userId));
    }
}
