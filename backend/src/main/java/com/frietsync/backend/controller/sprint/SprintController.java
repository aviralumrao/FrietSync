package com.frietsync.backend.controller.sprint;

import com.frietsync.backend.dto.sprint.CreateSprintRequest;
import com.frietsync.backend.dto.sprint.SprintResponse;
import com.frietsync.backend.dto.sprint.UpdateSprintRequest;
import com.frietsync.backend.service.sprint.SprintService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
public class SprintController {

    private final SprintService sprintService;

    @PostMapping("/projects/{projectId}/sprints")
    public ResponseEntity<SprintResponse> createSprint(
            @PathVariable UUID projectId,
            @Valid @RequestBody CreateSprintRequest request,
            @AuthenticationPrincipal UUID currentUserId) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(sprintService.createSprint(projectId, request, currentUserId));
    }

    @GetMapping("/projects/{projectId}/sprints")
    public ResponseEntity<List<SprintResponse>> listSprints(
            @PathVariable UUID projectId,
            @AuthenticationPrincipal UUID currentUserId) {
        return ResponseEntity.ok(sprintService.listSprints(projectId, currentUserId));
    }

    @GetMapping("/sprints/{id}")
    public ResponseEntity<SprintResponse> getSprint(
            @PathVariable UUID id,
            @AuthenticationPrincipal UUID currentUserId) {
        return ResponseEntity.ok(sprintService.getSprint(id, currentUserId));
    }

    @PatchMapping("/sprints/{id}")
    public ResponseEntity<SprintResponse> updateSprint(
            @PathVariable UUID id,
            @RequestBody UpdateSprintRequest request,
            @AuthenticationPrincipal UUID currentUserId) {
        return ResponseEntity.ok(sprintService.updateSprint(id, request, currentUserId));
    }

    @PostMapping("/sprints/{id}/start")
    public ResponseEntity<SprintResponse> startSprint(
            @PathVariable UUID id,
            @AuthenticationPrincipal UUID currentUserId) {
        return ResponseEntity.ok(sprintService.startSprint(id, currentUserId));
    }

    @PostMapping("/sprints/{id}/complete")
    public ResponseEntity<SprintResponse> completeSprint(
            @PathVariable UUID id,
            @AuthenticationPrincipal UUID currentUserId) {
        return ResponseEntity.ok(sprintService.completeSprint(id, currentUserId));
    }

    @DeleteMapping("/sprints/{id}")
    public ResponseEntity<Void> deleteSprint(
            @PathVariable UUID id,
            @AuthenticationPrincipal UUID currentUserId) {
        sprintService.deleteSprint(id, currentUserId);
        return ResponseEntity.noContent().build();
    }
}