package com.frietsync.backend.entity.thread;

import com.frietsync.backend.entity.common.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.Instant;
import java.util.UUID;

@Data
@EqualsAndHashCode(callSuper = false)
@Entity
@Table(name = "thread_comments",
        indexes = @Index(name = "idx_thread_comments_thread", columnList = "thread_id"))
public class ThreadComment extends BaseEntity {

    @Column(name = "thread_id", nullable = false, updatable = false)
    private UUID threadId;

    @Column(name = "parent_comment_id")
    private UUID parentCommentId;

    @Column(name = "author_id", nullable = false, updatable = false)
    private UUID authorId;

    @Column(nullable = false, length = 5000)
    private String content;

    @Column(nullable = false)
    private int upvotes = 0;

    @Column(name = "deleted_at")
    private Instant deletedAt;
}
