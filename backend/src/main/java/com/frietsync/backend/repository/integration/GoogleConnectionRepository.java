package com.frietsync.backend.repository.integration;

import com.frietsync.backend.entity.integration.GoogleConnection;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface GoogleConnectionRepository extends JpaRepository<GoogleConnection, UUID> {
    Optional<GoogleConnection> findByUser_Id(UUID userId);
}
