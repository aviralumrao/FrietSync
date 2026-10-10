package com.frietsync.backend.dto.workspace;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class CreateWorkspaceRequest {

    @NotBlank
    @Size(max = 100)
    private String name;
}