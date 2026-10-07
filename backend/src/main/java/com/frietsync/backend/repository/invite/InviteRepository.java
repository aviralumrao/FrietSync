package com.frietsync.backend.repository.invite;

import com.frietsync.backend.entity.invite.Invite;
import com.frietsync.backend.entity.invite.InviteStatus;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface InviteRepository extends JpaRepository<Invite, UUID> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select invite from Invite invite where invite.id = :inviteId")
    Optional<Invite> findByIdForUpdate(@Param("inviteId") UUID inviteId);

    List<Invite> findByEmailAndStatusOrderByCreatedAtDesc(String email, InviteStatus status);
}