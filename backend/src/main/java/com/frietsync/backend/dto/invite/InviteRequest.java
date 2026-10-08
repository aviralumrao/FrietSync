package com.frietsync.backend.dto.invite;

import com.frietsync.backend.entity.user.Role;
import lombok.Data;

import java.time.Instant;
import java.util.UUID;

@Data
public class InviteRequest {

    @NotBlank(message = "Email is required")
    @Email(message = "Email must be a valid email address")
    private String email;

    @NotBlank(message = "invite id is required")
    private UUID inviteId;

    private Instant expiresAt;
    private Role role;
}