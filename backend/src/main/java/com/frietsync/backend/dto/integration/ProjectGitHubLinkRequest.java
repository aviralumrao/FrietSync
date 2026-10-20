package com.frietsync.backend.dto.integration;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record ProjectGitHubLinkRequest(
        @NotBlank @Size(max = 255) @Pattern(regexp = "[A-Za-z0-9-]+") String owner,
        @NotBlank @Size(max = 255) @Pattern(regexp = "[A-Za-z0-9._-]+") String repository) {
}
