package com.frietsync.backend.dto.issue;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class IssueLabelRequest {

    @NotBlank(message = "Label name is required")
    @Size(max = 20, message = "Label name cannot exceed 20 characters")
    private String name;
}