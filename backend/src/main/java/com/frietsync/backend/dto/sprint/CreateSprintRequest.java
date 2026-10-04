package com.frietsync.backend.dto.sprint;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.time.LocalDate;

@Data
public class CreateSprintRequest {

    @NotBlank
    private String name;

    private String goal;

    @NotNull
    private LocalDate startDate;

    @NotNull
    private LocalDate endDate;
}