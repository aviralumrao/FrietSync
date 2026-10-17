package com.frietsync.backend.controller.replay;

import com.frietsync.backend.dto.replay.ReplayPhaseResponse;
import com.frietsync.backend.dto.replay.ReplaySnapshotResponse;
import com.frietsync.backend.dto.replay.ReplayTimelineResponse;
import com.frietsync.backend.dto.replay.UpdateReplayPhaseRequest;
import com.frietsync.backend.exception.BadRequestException;
import com.frietsync.backend.service.replay.ReplayService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.format.DateTimeParseException;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
public class ReplayController {

    private final ReplayService replayService;

    @GetMapping("/projects/{projectId}/replay/timeline")
    public ResponseEntity<ReplayTimelineResponse> timeline(@PathVariable UUID projectId,
                                                           @AuthenticationPrincipal UUID userId) {
        return ResponseEntity.ok(replayService.timeline(projectId, userId));
    }

    @PatchMapping("/projects/{projectId}/replay/phases/{phaseId}")
    public ResponseEntity<ReplayPhaseResponse> updatePhase(@PathVariable UUID projectId,
                                                           @PathVariable UUID phaseId,
                                                           @Valid @RequestBody UpdateReplayPhaseRequest request,
                                                           @AuthenticationPrincipal UUID userId) {
        return ResponseEntity.ok(replayService.updatePhase(projectId, phaseId, request, userId));
    }

    @GetMapping("/projects/{projectId}/replay/snapshot")
    public ResponseEntity<ReplaySnapshotResponse> snapshot(@PathVariable UUID projectId,
                                                           @RequestParam(required = false) String at,
                                                           @AuthenticationPrincipal UUID userId) {
        return ResponseEntity.ok(replayService.snapshot(projectId, parseMoment(at), userId));
    }

    private Instant parseMoment(String at) {
        if (at == null || at.isBlank()) {
            return null;
        }
        try {
            if (at.length() == 10) {
                return LocalDate.parse(at).plusDays(1).atStartOfDay(ZoneOffset.UTC).toInstant().minusSeconds(1);
            }
            return Instant.parse(at);
        } catch (DateTimeParseException ex) {
            throw new BadRequestException("Use a time like 2026-10-10T12:00:00Z or a date like 2026-10-10");
        }
    }
}