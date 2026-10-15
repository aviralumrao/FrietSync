package com.frietsync.backend.repository.poll;

import com.frietsync.backend.entity.poll.Poll;
import com.frietsync.backend.entity.poll.PollStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import jakarta.persistence.LockModeType;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface PollRepository extends JpaRepository<Poll, UUID> {
    List<Poll> findByProjectIdOrderByCreatedAtDesc(UUID projectId);

    List<Poll> findByStatusAndEndsAtLessThanEqual(PollStatus status, Instant endsAt);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select p from Poll p where p.id = :id")
    Optional<Poll> findByIdForUpdate(@Param("id") UUID id);
}
