package com.frietsync.backend.controller.invite;

import com.frietsync.backend.dto.invite.InviteActionRequest;
import com.frietsync.backend.dto.invite.InviteResponse;
import com.frietsync.backend.dto.invite.InviteRequest;
import com.frietsync.backend.service.invite.InviteService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1")
public class InviteController {

    private final InviteService inviteService;

    public InviteController(InviteService inviteService) {
        this.inviteService = inviteService;
    }

    @PostMapping("/admin/invites")
    public ResponseEntity<InviteResponse> create(@Valid @RequestBody InviteRequest request,
                                                 Authentication authentication) {
        UUID adminId = UUID.fromString(authentication.getName());
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(inviteService.createInvite(request, adminId));
    }

    @GetMapping("/invites/me")
    public ResponseEntity<List<InviteResponse>> myInvites(Authentication authentication) {
        UUID userId = UUID.fromString(authentication.getName());
        return ResponseEntity.ok(inviteService.myPendingInvites(userId));
    }

    @PostMapping("/invites/accept")
    public ResponseEntity<InviteResponse> accept(@Valid @RequestBody InviteActionRequest request,
                                                 Authentication authentication) {
        UUID userId = UUID.fromString(authentication.getName());
        return ResponseEntity.ok(inviteService.acceptInvite(userId, request.getInviteId()));
    }

    @PostMapping("/invites/reject")
    public ResponseEntity<InviteResponse> reject(@Valid @RequestBody InviteActionRequest request,
                                                 Authentication authentication) {
        UUID userId = UUID.fromString(authentication.getName());
        return ResponseEntity.ok(inviteService.rejectInvite(userId, request.getInviteId()));
    }

    @PostMapping("/admin/invites/revoke")
    public ResponseEntity<InviteResponse> revoke(
            @Valid @RequestBody InviteActionRequest request,
            Authentication authentication) {
        UUID adminId = UUID.fromString(authentication.getName());
        return ResponseEntity.ok(inviteService.revokeInvite(adminId, request.getInviteId()));
    }
}