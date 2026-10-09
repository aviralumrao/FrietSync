package com.frietsync.backend.dto.invite;

import com.frietsync.backend.entity.invite.InviteStatus;
import com.frietsync.backend.entity.user.Role;
import lombok.Data;

import java.time.Instant;
import java.util.UUID;

@Data
public class InviteResponse {
    private UUID id;
    private String email;
    private Role role;
    private InviteStatus status;
    private String invitedByName;
    private String invitedByEmail;
    private UUID workspaceId;
    private Instant createdAt;
    private Instant acceptedAt;
    private Instant expiresAt;
}