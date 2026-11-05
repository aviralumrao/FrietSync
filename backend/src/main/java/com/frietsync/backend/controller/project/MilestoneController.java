package com.frietsync.backend.controller.project;

import com.frietsync.backend.dto.project.MilestoneRequest;
import com.frietsync.backend.dto.project.MilestoneResponse;
import com.frietsync.backend.dto.project.UpdateMilestoneRequest;
import com.frietsync.backend.service.project.MilestoneService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
public class MilestoneController {

    private final MilestoneService milestoneService;

    @PostMapping("/projects/{projectId}/milestones")
    public ResponseEntity<MilestoneResponse> create(
            @PathVariable UUID projectId,
            @Valid @RequestBody MilestoneRequest request,
            @AuthenticationPrincipal UUID userId) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(milestoneService.create(projectId, userId, request));
    }

    @GetMapping("/projects/{projectId}/milestones")
    public ResponseEntity<List<MilestoneResponse>> list(
            @PathVariable UUID projectId,
            @AuthenticationPrincipal UUID userId) {
        return ResponseEntity.ok(milestoneService.list(projectId, userId));
    }

    @PatchMapping("/milestones/{milestoneId}")
    public ResponseEntity<MilestoneResponse> update(
            @PathVariable UUID milestoneId,
            @Valid @RequestBody UpdateMilestoneRequest request,
            @AuthenticationPrincipal UUID userId) {
        return ResponseEntity.ok(milestoneService.update(milestoneId, userId, request));
    }

    @DeleteMapping("/milestones/{milestoneId}")
    public ResponseEntity<Void> delete(
            @PathVariable UUID milestoneId,
            @AuthenticationPrincipal UUID userId) {
        milestoneService.delete(milestoneId, userId);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/milestones/{milestoneId}/restore")
    public ResponseEntity<MilestoneResponse> restore(
            @PathVariable UUID milestoneId,
            @AuthenticationPrincipal UUID userId) {
        return ResponseEntity.ok(milestoneService.restore(milestoneId, userId));
    }
}
