package com.frietsync.backend.dto.project;

import com.frietsync.backend.entity.user.Role;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Future;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.time.Instant;

@Data
public class ProjectInviteRequest {

    @NotBlank(message = "Email is required")
    @Email(message = "Email is not valid")
    private String email;

    @NotNull(message = "Project role is required")
    private Role role;

    @Future
    private Instant expiresAt;
}
