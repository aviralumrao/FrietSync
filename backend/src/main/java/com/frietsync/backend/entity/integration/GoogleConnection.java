package com.frietsync.backend.entity.integration;

import com.frietsync.backend.entity.common.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;
import com.frietsync.backend.entity.user.User;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "google_connections")
@Getter
@Setter
@NoArgsConstructor
public class GoogleConnection extends BaseEntity {
    @OneToOne(optional = false)
    @JoinColumn(name = "user_id", nullable = false, unique = true)
    private User user;

    @Column(name = "google_subject", length = 255)
    private String googleSubject;

    @Column(name = "google_email", length = 320)
    private String googleEmail;

    @Column(name = "encrypted_refresh_token", nullable = false, length = 2048)
    private String encryptedRefreshToken;

    @Column(name = "needs_reauthorization", nullable = false)
    private boolean needsReauthorization;
}
