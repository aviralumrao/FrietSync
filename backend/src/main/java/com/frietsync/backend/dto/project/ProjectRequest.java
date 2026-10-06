package com.frietsync.backend.dto.project;

import com.frietsync.backend.entity.project.ProjectStatus;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.time.LocalDate;
import java.util.UUID;

@Data
public class ProjectRequest {

    @NotBlank(message = "Project name is required")
    @Size(max = 100, message = "Project name must be at most 100 characters")
    private String name;

    @Size(max = 1000, message = "Description must be at most 1000 characters")
    private String description;

    private UUID workspaceId;

    private ProjectStatus status;

    private LocalDate startDate;

    private LocalDate deadline;

    @NotBlank(message = "Project manager email is required")
    @Email(message = "Project manager email is not valid")
    private String projectManagerEmail;

    @Email(message = "Team lead email is not valid")
    private String teamLeadEmail;
}
