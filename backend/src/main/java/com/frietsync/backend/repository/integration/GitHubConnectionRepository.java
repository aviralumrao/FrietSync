package com.frietsync.backend.repository.integration;

import com.frietsync.backend.entity.integration.GitHubConnection;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;
import java.util.UUID;

public interface GitHubConnectionRepository extends JpaRepository<GitHubConnection, UUID> {
    Optional<GitHubConnection> findByUser_Id(UUID userId);
}
