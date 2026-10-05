package com.frietsync.backend.repository.sprint;

import com.frietsync.backend.entity.sprint.SprintAssignmentRequest;
import com.frietsync.backend.entity.sprint.SprintAssignmentRequestStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface SprintAssignmentRequestRepository extends JpaRepository<SprintAssignmentRequest, UUID> {
    boolean existsBySprintIdAndRequestedByAndStatus(
            UUID sprintId,
            UUID requestedBy,
            SprintAssignmentRequestStatus status);

    List<SprintAssignmentRequest> findBySprintIdAndStatus(
            UUID sprintId,
            SprintAssignmentRequestStatus status);

    List<SprintAssignmentRequest> findBySprintIdInOrderByCreatedAtDesc(List<UUID> sprintIds);

    Optional<SprintAssignmentRequest> findByIdAndSprintId(UUID id, UUID sprintId);

    void deleteBySprintId(UUID sprintId);

    @Query("select request from SprintAssignmentRequest request "
            + "where request.sprintId in (select sprint.id from Sprint sprint where sprint.projectId = :projectId) "
            + "order by request.createdAt desc")
    List<SprintAssignmentRequest> findAllForProject(@Param("projectId") UUID projectId);
}
