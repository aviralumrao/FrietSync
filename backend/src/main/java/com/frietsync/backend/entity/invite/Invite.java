package com.frietsync.backend.entity.invite;

import com.frietsync.backend.entity.common.BaseEntity;
import com.frietsync.backend.entity.invite.InviteStatus;
import com.frietsync.backend.entity.user.Role;
import jakarta.persistence.*;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.Instant;
import java.util.UUID;

@Data
@EqualsAndHashCode(callSuper = false)
@Entity
@Table(name = "invites", indexes = {
        @Index(name = "idx_invites_email", columnList = "email")
})
public class Invite extends BaseEntity {

    @Column(nullable = false)
    private String email;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Role role;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private InviteStatus status = InviteStatus.PENDING;

    @Column(name = "invited_by", nullable = false)
    private UUID invitedBy;

    @Column(name = "workspace_id", nullable = false)
    private UUID workspaceId;

    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;

    @Column(name = "accepted_at", nullable = true)
    private Instant acceptedAt;

    @Column(name = "project_id")
    private UUID projectId;

}