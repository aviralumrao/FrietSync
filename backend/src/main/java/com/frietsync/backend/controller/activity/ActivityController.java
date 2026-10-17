package com.frietsync.backend.controller.activity;

import com.frietsync.backend.dto.activity.ActivityResponse;
import com.frietsync.backend.dto.activity.PresenceResponse;
import com.frietsync.backend.dto.activity.ProjectSummaryResponse;
import com.frietsync.backend.dto.issue.IssuePageResponse;
import com.frietsync.backend.entity.issue.IssueHistoryAction;
import com.frietsync.backend.exception.BadRequestException;
import com.frietsync.backend.service.activity.ActivityService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
public class ActivityController {

    private final ActivityService activityService;

    @GetMapping("/projects/{projectId}/presence")
    public ResponseEntity<List<PresenceResponse>> presence(@PathVariable UUID projectId,
                                                           @AuthenticationPrincipal UUID userId) {
        return ResponseEntity.ok(activityService.presence(projectId, userId));
    }

    @GetMapping("/projects/{projectId}/activity")
    public ResponseEntity<List<ActivityResponse>> activity(@PathVariable UUID projectId,
                                                           @RequestParam(defaultValue = "50") int limit,
                                                           @AuthenticationPrincipal UUID userId) {
        if (limit < 1 || limit > 100) {
            throw new BadRequestException("Limit must be between 1 and 100");
        }
        return ResponseEntity.ok(activityService.recentActivity(projectId, userId, limit));
    }

    @GetMapping("/projects/{projectId}/events")
    public ResponseEntity<IssuePageResponse<ActivityResponse>> events(
            @PathVariable UUID projectId,
            @RequestParam(required = false) IssueHistoryAction action,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @AuthenticationPrincipal UUID userId) {
        if (page < 0) {
            throw new BadRequestException("Page must not be negative");
        }
        if (size < 1 || size > 100) {
            throw new BadRequestException("Size must be between 1 and 100");
        }
        return ResponseEntity.ok(activityService.events(projectId, userId, action, page, size));
    }

    @GetMapping("/projects/{projectId}/summary")
    public ResponseEntity<ProjectSummaryResponse> summary(@PathVariable UUID projectId,
                                                          @AuthenticationPrincipal UUID userId) {
        return ResponseEntity.ok(activityService.summary(projectId, userId));
    }

    @GetMapping("/projects/{projectId}/summary/pdf")
    public ResponseEntity<byte[]> summaryPdf(@PathVariable UUID projectId,
                                             @AuthenticationPrincipal UUID userId) {
        byte[] pdf = activityService.summaryPdf(projectId, userId);
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment()
                        .filename("project-summary-" + projectId + ".pdf").build().toString())
                .contentType(MediaType.APPLICATION_PDF)
                .body(pdf);
    }
}