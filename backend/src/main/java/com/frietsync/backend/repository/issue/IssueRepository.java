package com.frietsync.backend.repository.issue;

import com.frietsync.backend.entity.issue.Issue;
import com.frietsync.backend.entity.issue.IssueStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.jpa.repository.Lock;
import jakarta.persistence.LockModeType;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.UUID;

public interface IssueRepository extends JpaRepository<Issue, UUID> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select i from Issue i where i.id = :issueId")
    java.util.Optional<Issue> findByIdForUpdate(@Param("issueId") UUID issueId);

    Page<Issue> findByProjectIdAndDeletedAtIsNull(UUID projectId, Pageable pageable);

    @Query(value = "select i from Issue i where i.deletedAt is null and i.projectId in "
            + "(select p.id from Project p where p.workspaceId = :workspaceId) and i.id in "
            + "(select a.issueId from IssueAssignee a where a.userId = :userId)",
            countQuery = "select count(i) from Issue i where i.deletedAt is null and i.projectId in "
                    + "(select p.id from Project p where p.workspaceId = :workspaceId) and i.id in "
                    + "(select a.issueId from IssueAssignee a where a.userId = :userId)")
    Page<Issue> findAssignedToUserInWorkspace(
            @Param("userId") UUID userId,
            @Param("workspaceId") UUID workspaceId,
            Pageable pageable
    );

    List<Issue> findByProjectIdAndDeletedAtIsNull(UUID projectId);

    long countByProjectIdAndDeletedAtIsNull(UUID projectId);

    List<Issue> findByProjectIdAndDeletedAtIsNullOrderByCreatedAtAsc(UUID projectId);

    List<Issue> findByProjectIdOrderByCreatedAtDesc(UUID projectId);

    List<Issue> findByProjectId(UUID projectId);

    List<Issue> findByMilestoneIdAndDeletedAtIsNull(UUID milestoneId);

    List<Issue> findByMilestoneId(UUID milestoneId);

    @Modifying
    @Query("update Issue i set i.milestoneId = null where i.milestoneId = :milestoneId")
    int clearMilestoneLink(@Param("milestoneId") UUID milestoneId);

    List<Issue> findByGithubIssueNumberIsNotNullAndDeletedAtIsNull();

    List<Issue> findByCreatedByAndDeletedAtIsNull(UUID createdBy);

    long countByProjectIdAndStatusAndDeletedAtIsNull(UUID projectId, IssueStatus status);

    @Modifying
    @Query(value = "UPDATE issues SET embedding = CAST(:embedding AS vector) WHERE id = :issueId",
            nativeQuery = true)
    int updateEmbedding(@Param("issueId") UUID issueId, @Param("embedding") String embedding);

    @Modifying
    @Query(value = "UPDATE issues SET embedding = CAST(:embedding AS vector) "
            + "WHERE id = :issueId AND embedding IS NULL",
            nativeQuery = true)
    int updateEmbeddingIfMissing(@Param("issueId") UUID issueId, @Param("embedding") String embedding);

    @Query(value = "SELECT * FROM issues WHERE embedding IS NULL AND deleted_at IS NULL "
            + "ORDER BY created_at ASC LIMIT :limit",
            nativeQuery = true)
    List<Issue> findIssuesWithoutEmbedding(@Param("limit") int limit);

    @Query(value = "SELECT i.id AS \"issueId\", i.project_id AS \"projectId\", p.name AS \"projectName\", "
            + "i.title AS title, i.description AS description, i.status AS status, i.priority AS priority, "
            + "1 - (i.embedding <=> CAST(:queryEmbedding AS vector)) AS similarity "
            + "FROM issues i JOIN projects p ON p.id = i.project_id "
            + "JOIN users u ON u.id = :userId AND u.workspace_id = p.workspace_id "
            + "JOIN workspaces w ON w.id = p.workspace_id AND w.deletion_requested_at IS NULL "
            + "WHERE i.embedding IS NOT NULL AND i.deleted_at IS NULL AND p.deleted_at IS NULL "
            + "AND (p.admin_id = :userId OR p.project_manager_id = :userId OR p.team_lead_id = :userId "
            + "OR EXISTS (SELECT 1 FROM project_members pm "
            + "WHERE pm.project_id = p.id AND pm.user_id = :userId)) "
            + "ORDER BY i.embedding <=> CAST(:queryEmbedding AS vector) LIMIT :limit",
            nativeQuery = true)
    List<SemanticIssueSearchResult> searchSemanticallyForUser(
            @Param("userId") UUID userId,
            @Param("queryEmbedding") String queryEmbedding,
            @Param("limit") int limit);

    @Query("select i from Issue i where i.projectId = :projectId and i.deletedAt is null "
            + "and (:status is null or i.status = :status) "
            + "and (lower(i.title) like lower(concat('%', :query, '%')) "
            + "or lower(coalesce(i.description, '')) like lower(concat('%', :query, '%'))) "
            + "order by i.updatedAt desc")
    List<Issue> searchProjectIssues(@Param("projectId") UUID projectId,
                                    @Param("query") String query,
                                    @Param("status") IssueStatus status,
                                    Pageable pageable);

    @Query("select i from Issue i where i.deletedAt is null and i.id in "
            + "(select a.issueId from IssueAssignee a where a.userId = :userId)")
    List<Issue> findAssignedTo(@Param("userId") UUID userId);

    @Query("select i from Issue i where i.deletedAt is null and i.projectId in "
            + "(select p.id from Project p where p.workspaceId = :workspaceId) and i.id in "
            + "(select a.issueId from IssueAssignee a where a.userId = :userId)")
    List<Issue> findAssignedToUserInWorkspace(@Param("userId") UUID userId,
                                              @Param("workspaceId") UUID workspaceId);

    @Query("select i from Issue i where i.deletedAt is null and i.id in "
            + "(select h.issueId from IssueHistory h where h.actorId = :userId "
            + "and h.action = com.frietsync.backend.entity.issue.IssueHistoryAction.RESOLVED)")
    List<Issue> findResolvedBy(@Param("userId") UUID userId);

    @Modifying
    @Query("update Issue i set i.githubIssueNumber = null, i.githubIssueTitle = null, "
            + "i.githubIssueUrl = null, i.githubIssueRepository = null where i.projectId = :projectId")
    int clearGitHubLinksByProjectId(@Param("projectId") UUID projectId);
}
