package com.frietsync.backend.repository.sprint;

import com.frietsync.backend.entity.sprint.Sprint;
import com.frietsync.backend.entity.sprint.SprintStatus;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface SprintRepository extends JpaRepository<Sprint, UUID> {
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select sprint from Sprint sprint where sprint.id = :sprintId")
    Optional<Sprint> findByIdForUpdate(@Param("sprintId") UUID sprintId);

    List<Sprint> findByProjectId(UUID projectId);

    List<Sprint> findByIssueId(UUID issueId);

    List<Sprint> findByIssueIdOrderByCreatedAtDesc(UUID issueId);

    List<Sprint> findByIssueIdIn(List<UUID> issueIds);

    @Modifying
    @Query("update Sprint sprint set sprint.issueId = null where sprint.issueId = :issueId")
    void clearIssueLink(@Param("issueId") UUID issueId);

    List<Sprint> findByAssignedContributorId(UUID assignedContributorId);

    @Query("select sprint from Sprint sprint where sprint.assignedContributorId = :userId "
            + "and sprint.projectId in (select project.id from Project project "
            + "where project.workspaceId = :workspaceId) order by sprint.createdAt desc")
    List<Sprint> findAssignedToUserInWorkspace(@Param("userId") UUID userId,
                                              @Param("workspaceId") UUID workspaceId);

    long countByProjectId(UUID projectId);

    long countByProjectIdAndStatus(UUID projectId, SprintStatus status);

    boolean existsByProjectIdAndAssignedContributorId(UUID projectId, UUID assignedContributorId);

    void deleteByProjectId(UUID projectId);

    @Query("select max(sprint.sprintNumber) from Sprint sprint where sprint.projectId = :projectId")
    Integer findMaxSprintNumber(@Param("projectId") UUID projectId);
}
