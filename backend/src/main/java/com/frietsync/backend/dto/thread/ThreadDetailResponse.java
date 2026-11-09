package com.frietsync.backend.dto.thread;

import lombok.Data;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Data
public class ThreadDetailResponse {
    private UUID id;
    private UUID projectId;
    private UUID authorId;
    private String authorName;
    private String title;
    private String content;
    private int upvotes;
    private Instant createdAt;
    private Instant updatedAt;
    private List<CommentResponse> comments;
}
