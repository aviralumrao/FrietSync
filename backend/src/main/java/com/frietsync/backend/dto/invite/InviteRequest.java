package com.frietsync.backend.dto.invite;

import com.frietsync.backend.entity.invite.InvitePurpose;
import com.frietsync.backend.entity.user.Role;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Future;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.time.Instant;
import java.util.UUID;

@Data
public class InviteRequest {

    @NotBlank
    @Email(message = "Email must be a valid email address")
    private String email;

    private UUID inviteId;

    @Future
    private Instant expiresAt;
    private Role role;

    @NotNull
    private InvitePurpose purpose;

    private UUID projectId;
}