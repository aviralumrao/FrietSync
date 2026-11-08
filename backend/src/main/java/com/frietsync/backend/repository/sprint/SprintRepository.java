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

    List<Sprint> findByProjectIdAndDeletedAtIsNull(UUID projectId);

    List<Sprint> findByIssueId(UUID issueId);

    List<Sprint> findByIssueIdAndDeletedAtIsNull(UUID issueId);

    List<Sprint> findByIssueIdOrderByCreatedAtDesc(UUID issueId);

    List<Sprint> findByIssueIdAndDeletedAtIsNullOrderByCreatedAtDesc(UUID issueId);

    List<Sprint> findByIssueIdIn(List<UUID> issueIds);

    List<Sprint> findByIssueIdInAndDeletedAtIsNull(List<UUID> issueIds);

    @Modifying
    @Query("update Sprint sprint set sprint.issueId = null where sprint.issueId = :issueId")
    void clearIssueLink(@Param("issueId") UUID issueId);

    List<Sprint> findByAssignedContributorId(UUID assignedContributorId);

    List<Sprint> findByAssignedContributorIdAndDeletedAtIsNull(UUID assignedContributorId);

    @Query("select sprint from Sprint sprint where sprint.deletedAt is null and sprint.assignedContributorId = :userId "
            + "and sprint.projectId in (select project.id from Project project "
            + "where project.workspaceId = :workspaceId and project.deletedAt is null) order by sprint.createdAt desc")
    List<Sprint> findAssignedToUserInWorkspace(@Param("userId") UUID userId,
                                              @Param("workspaceId") UUID workspaceId);

    long countByProjectId(UUID projectId);

    long countByProjectIdAndDeletedAtIsNull(UUID projectId);

    long countByProjectIdAndStatus(UUID projectId, SprintStatus status);

    long countByProjectIdAndStatusAndDeletedAtIsNull(UUID projectId, SprintStatus status);

    boolean existsByProjectIdAndAssignedContributorId(UUID projectId, UUID assignedContributorId);

    boolean existsByProjectIdAndAssignedContributorIdAndDeletedAtIsNull(UUID projectId, UUID assignedContributorId);

    void deleteByProjectId(UUID projectId);

    @Query("select max(sprint.sprintNumber) from Sprint sprint where sprint.projectId = :projectId and sprint.deletedAt is null")
    Integer findMaxSprintNumber(@Param("projectId") UUID projectId);

    @Modifying
    @Query(value = "UPDATE sprints SET embedding = CAST(:embedding AS vector) WHERE id = :sprintId", nativeQuery = true)
    int updateEmbedding(@Param("sprintId") UUID sprintId, @Param("embedding") String embedding);

    @Modifying
    @Query(value = "UPDATE sprints SET embedding = CAST(:embedding AS vector) "
            + "WHERE id = :sprintId AND embedding IS NULL", nativeQuery = true)
    int updateEmbeddingIfMissing(@Param("sprintId") UUID sprintId, @Param("embedding") String embedding);

    @Query(value = "SELECT * FROM sprints WHERE embedding IS NULL AND deleted_at IS NULL "
            + "ORDER BY created_at ASC LIMIT :limit", nativeQuery = true)
    List<Sprint> findSprintsWithoutEmbedding(@Param("limit") int limit);

    @Query(value = "SELECT s.id AS \"sprintId\", s.project_id AS \"projectId\", p.name AS \"projectName\", "
            + "s.name AS title, s.goal AS description, s.status AS status, "
            + "NULL AS priority, 1 - (s.embedding <=> CAST(:queryEmbedding AS vector)) AS similarity "
            + "FROM sprints s JOIN projects p ON p.id = s.project_id "
            + "JOIN users u ON u.id = :userId AND u.workspace_id = p.workspace_id "
            + "JOIN workspaces w ON w.id = p.workspace_id AND w.deletion_requested_at IS NULL "
            + "WHERE s.embedding IS NOT NULL AND s.deleted_at IS NULL AND p.deleted_at IS NULL "
            + "AND (p.admin_id = :userId OR p.project_manager_id = :userId OR p.team_lead_id = :userId "
            + "OR EXISTS (SELECT 1 FROM project_members pm WHERE pm.project_id = p.id AND pm.user_id = :userId)) "
            + "ORDER BY s.embedding <=> CAST(:queryEmbedding AS vector) LIMIT :limit", nativeQuery = true)
    List<SprintSemanticSearchResult> searchSemanticallyForUser(
            @Param("userId") UUID userId,
            @Param("queryEmbedding") String queryEmbedding,
            @Param("limit") int limit);
}
