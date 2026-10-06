package com.frietsync.backend.dto.project;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class LabelRequest {

    @NotBlank(message = "Label name is required")
    @Size(max = 50, message = "Label name must be at most 50 characters")
    private String name;

    @Pattern(regexp = "^#[0-9a-fA-F]{6}$", message = "Color must be a hex code like #FF5733")
    private String color;
}
