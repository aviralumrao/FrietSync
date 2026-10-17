package com.frietsync.backend.entity.integration;

import com.frietsync.backend.entity.common.BaseEntity;
import com.frietsync.backend.entity.user.User;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;

@Entity
@Table(name = "google_oauth_states")
@Getter
@Setter
@NoArgsConstructor
public class GoogleOAuthState extends BaseEntity {
    @Column(name = "state_hash", nullable = false, unique = true, length = 64)
    private String stateHash;

    @ManyToOne(optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;
}
