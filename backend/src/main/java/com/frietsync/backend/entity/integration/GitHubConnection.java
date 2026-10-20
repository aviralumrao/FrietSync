package com.frietsync.backend.entity.integration;

import com.frietsync.backend.entity.common.BaseEntity;
import com.frietsync.backend.entity.user.User;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;
import lombok.Data;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "github_connections")
@Data
@NoArgsConstructor
public class GitHubConnection extends BaseEntity {
    @OneToOne(optional = false)
    @JoinColumn(name = "user_id", nullable = false, unique = true)
    private User user;

    @Column(name = "github_login", nullable = false, length = 255)
    private String githubLogin;

    @Column(name = "encrypted_access_token", nullable = false, length = 4096)
    private String encryptedAccessToken;
}
