package com.frietsync.backend.controller.meeting;

import com.frietsync.backend.dto.meeting.CreateMeetingRequest;
import com.frietsync.backend.dto.meeting.MeetingResponse;
import com.frietsync.backend.dto.meeting.UpdateMeetingRequest;
import com.frietsync.backend.service.meeting.MeetingService;
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
public class MeetingController {

    private final MeetingService meetingService;

    @PostMapping("/projects/{projectId}/meetings")
    public ResponseEntity<MeetingResponse> schedule(
            @PathVariable UUID projectId,
            @Valid @RequestBody CreateMeetingRequest request,
            @AuthenticationPrincipal UUID organizerId) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(meetingService.create(projectId, organizerId, request));
    }

    @GetMapping("/meetings")
    public ResponseEntity<List<MeetingResponse>> myMeetings(
            @AuthenticationPrincipal UUID userId) {
        return ResponseEntity.ok(meetingService.getMyMeetings(userId));
    }

    @GetMapping("/projects/{projectId}/meetings")
    public ResponseEntity<List<MeetingResponse>> projectMeetings(
            @PathVariable UUID projectId,
            @AuthenticationPrincipal UUID userId) {
        return ResponseEntity.ok(meetingService.getProjectMeetings(projectId, userId));
    }

    @PatchMapping("/meetings/{meetingId}")
    public ResponseEntity<MeetingResponse> update(
            @PathVariable UUID meetingId,
            @Valid @RequestBody UpdateMeetingRequest request,
            @AuthenticationPrincipal UUID userId) {
        return ResponseEntity.ok(meetingService.update(meetingId, userId, request));
    }

    @DeleteMapping("/meetings/{meetingId}")
    public ResponseEntity<Void> cancel(
            @PathVariable UUID meetingId,
            @AuthenticationPrincipal UUID userId) {
        meetingService.cancel(meetingId, userId);
        return ResponseEntity.noContent().build();
    }

}
