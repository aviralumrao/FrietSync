package com.frietsync.backend.dto.issue;

import com.frietsync.backend.entity.issue.IssuePriority;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.time.LocalDate;

@Data
public class IssueCreateV2Request {
    @NotBlank(message = "Title is required")
    @Size(max = 200, message = "Title must be at most 200 characters")
    private String title;

    @Size(max = 1000, message = "Description must be at most 1000 characters")
    private String description;

    private IssuePriority priority;
    private LocalDate dueDate;

    @PositiveOrZero(message = "Estimated hours cannot be negative")
    private Double estimatedHours;

    @NotNull(message = "githubAction is required")
    private GitHubIssueAction githubAction;

    @Min(value = 1, message = "GitHub issue number must be positive")
    private Integer githubIssueNumber;
}
