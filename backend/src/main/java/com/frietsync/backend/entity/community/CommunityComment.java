package com.frietsync.backend.entity.community;

import com.frietsync.backend.entity.common.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.Instant;
import java.util.UUID;

@Data
@EqualsAndHashCode(callSuper = false)
@Entity
@Table(name = "community_comments")
public class CommunityComment extends BaseEntity {

    @Column(name = "post_id", nullable = false)
    private UUID postId;

    @Column(name = "workspace_id", nullable = false)
    private UUID workspaceId;

    @Column(name = "author_id", nullable = false)
    private UUID authorId;

    @Column(name = "author_name", nullable = false)
    private String authorName;

    @Column(nullable = false, length = 1000)
    private String content;

    @Column(name = "edited_at")
    private Instant editedAt;
}