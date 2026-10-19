package com.frietsync.backend.controller.call;

import com.frietsync.backend.dto.call.CallResponse;
import com.frietsync.backend.service.call.CallService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/projects/{projectId}/calls")
@RequiredArgsConstructor
public class CallController {

    private final CallService callService;

    @PostMapping
    public ResponseEntity<CallResponse> startCall(
            @PathVariable UUID projectId,
            @AuthenticationPrincipal UUID userId) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(CallResponse.from(callService.startCall(projectId, userId)));
    }

    @GetMapping("/active")
    public ResponseEntity<CallResponse> getActiveCall(
            @PathVariable UUID projectId,
            @AuthenticationPrincipal UUID userId) {
        return ResponseEntity.ok(CallResponse.from(callService.getActiveCall(projectId, userId)));
    }

    @PostMapping("/{callId}/join")
    public ResponseEntity<CallResponse> joinCall(
            @PathVariable UUID projectId,
            @PathVariable UUID callId,
            @AuthenticationPrincipal UUID userId) {
        return ResponseEntity.ok(CallResponse.from(callService.joinCall(projectId, callId, userId)));
    }

    @PostMapping("/{callId}/leave")
    public ResponseEntity<Void> leaveCall(
            @PathVariable UUID projectId,
            @PathVariable UUID callId,
            @AuthenticationPrincipal UUID userId) {
        callService.leaveCall(projectId, callId, userId);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/{callId}/end")
    public ResponseEntity<CallResponse> endCall(
            @PathVariable UUID projectId,
            @PathVariable UUID callId,
            @AuthenticationPrincipal UUID userId) {
        return ResponseEntity.ok(CallResponse.from(callService.endCall(projectId, callId, userId)));
    }
}
