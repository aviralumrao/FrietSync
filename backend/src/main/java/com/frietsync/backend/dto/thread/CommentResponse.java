package com.frietsync.backend.dto.thread;

import lombok.Data;

import java.time.Instant;
import java.util.UUID;

@Data
public class CommentResponse {
    private UUID id;
    private UUID threadId;
    private UUID parentCommentId;
    private UUID authorId;
    private String authorName;
    private String content;
    private int upvotes;
    private Instant createdAt;
    private Instant updatedAt;
}
