package com.frietsync.backend.dto.sprint;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.time.LocalDate;

@Data
public class CreateSprintRequest {

    @NotBlank(message="Sprint must have a Name")
    private String name;

    @NotNull
    private String goal;

    @NotNull(message = "Start date is required")
    private LocalDate startDate;

    @NotNull(message = "End date is required")
    private LocalDate endDate;
}