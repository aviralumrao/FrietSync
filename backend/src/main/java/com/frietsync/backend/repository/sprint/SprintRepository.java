package com.frietsync.backend.repository.sprint;

import com.frietsync.backend.entity.sprint.Sprint;
import com.frietsync.backend.entity.sprint.SprintStatus;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface SprintRepository extends JpaRepository<Sprint, UUID> {
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select sprint from Sprint sprint where sprint.id = :sprintId")
    Optional<Sprint> findByIdForUpdate(@Param("sprintId") UUID sprintId);

    List<Sprint> findByProjectId(UUID projectId);

    List<Sprint> findByMilestoneId(UUID milestoneId);

    List<Sprint> findByAssignedContributorId(UUID assignedContributorId);

    long countByProjectId(UUID projectId);

    long countByProjectIdAndStatus(UUID projectId, SprintStatus status);

    long countByMilestoneId(UUID milestoneId);

    long countByMilestoneIdAndStatus(UUID milestoneId, SprintStatus status);

    boolean existsByProjectIdAndAssignedContributorId(UUID projectId, UUID assignedContributorId);

    void deleteByProjectId(UUID projectId);

    @Query("select max(sprint.sprintNumber) from Sprint sprint where sprint.projectId = :projectId")
    Integer findMaxSprintNumber(@Param("projectId") UUID projectId);
}