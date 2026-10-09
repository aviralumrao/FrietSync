package com.frietsync.backend.dto.project;

import com.frietsync.backend.entity.project.ProjectRole;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class MemberRequest {

    @NotBlank(message = "Email is required")
    @Email(message = "Email is not valid")
    private String email;

    @NotNull(message = "Role is required")
    private ProjectRole role;
}