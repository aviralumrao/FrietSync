package com.frietsync.backend.repository.call;

import com.frietsync.backend.entity.call.CallSession;
import com.frietsync.backend.entity.call.CallType;

import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface CallSessionRepository
        extends JpaRepository<CallSession, UUID> {

    Optional<CallSession> findByProjectIdAndStatus(
            UUID projectId,
            CallSession.CallStatus status
    );

    Optional<CallSession> findByProjectIdAndTypeAndStatus(
            UUID projectId,
            CallType type,
            CallSession.CallStatus status
    );

    List<CallSession> findByStatus(CallSession.CallStatus status);

    Optional<CallSession> findByIdAndProjectId(UUID id, UUID projectId);

    boolean existsByRoomId(Long roomId);

    List<CallSession> findTop50ByStatusAndJanusRoomDestroyedFalseOrderByEndedAtAsc(
            CallSession.CallStatus status
    );

    @Modifying
    @Transactional
    @Query("update CallSession call set call.janusRoomDestroyed = true where call.id = :callId")
    int markJanusRoomDestroyed(@Param("callId") UUID callId);
}