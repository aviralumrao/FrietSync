package com.frietsync.backend.repository.integration;

import com.frietsync.backend.entity.integration.GoogleOAuthState;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;
import java.util.UUID;

public interface GoogleOAuthStateRepository extends JpaRepository<GoogleOAuthState, UUID> {
    boolean existsByStateHashAndExpiresAtAfter(String stateHash, java.time.Instant now);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select s from GoogleOAuthState s where s.stateHash = :hash")
    Optional<GoogleOAuthState> findByHashForUpdate(@Param("hash") String hash);
}
