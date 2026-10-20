package com.frietsync.backend.repository.integration;

import com.frietsync.backend.entity.integration.GitHubOAuthState;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.util.Optional;
import java.util.UUID;

public interface GitHubOAuthStateRepository extends JpaRepository<GitHubOAuthState, UUID> {
    boolean existsByStateHashAndExpiresAtAfter(String stateHash, java.time.Instant now);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select s from GitHubOAuthState s where s.stateHash = :hash")
    Optional<GitHubOAuthState> findByHashForUpdate(@Param("hash") String hash);
}
