package com.frietsync.backend.dto.issue;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.util.List;
import java.util.UUID;

@Data
public class CreateIssueCommentRequest {
    @NotBlank
    @Size(max = 200)
    private String content;

    @Size(max = 50)
    private List<UUID> mentionedUserIds;
}
