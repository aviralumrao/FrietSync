package com.frietsync.backend.repository.call;

import com.frietsync.backend.entity.call.CallSession;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface CallSessionRepository
        extends JpaRepository<CallSession, UUID> {

    Optional<CallSession> findByProjectIdAndStatus(
            UUID projectId,
            CallSession.CallStatus status
    );

    Optional<CallSession> findByIdAndProjectId(UUID id, UUID projectId);

    boolean existsByRoomId(Long roomId);
}
