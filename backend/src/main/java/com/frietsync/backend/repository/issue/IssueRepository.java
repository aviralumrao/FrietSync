package com.frietsync.backend.repository.issue;

import com.frietsync.backend.entity.issue.Issue;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.UUID;

public interface IssueRepository extends JpaRepository<Issue, UUID> {

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

    List<Issue> findByProjectIdAndDeletedAtIsNullOrderByCreatedAtAsc(UUID projectId);

    List<Issue> findByProjectIdOrderByCreatedAtDesc(UUID projectId);

    List<Issue> findByProjectId(UUID projectId);

    List<Issue> findByCreatedByAndDeletedAtIsNull(UUID createdBy);

    @Query("select i from Issue i where i.deletedAt is null and i.id in "
            + "(select a.issueId from IssueAssignee a where a.userId = :userId)")
    List<Issue> findAssignedTo(@Param("userId") UUID userId);

    @Query("select i from Issue i where i.deletedAt is null and i.id in "
            + "(select h.issueId from IssueHistory h where h.actorId = :userId "
            + "and h.action = com.frietsync.backend.entity.issue.IssueHistoryAction.RESOLVED)")
    List<Issue> findResolvedBy(@Param("userId") UUID userId);

    @Modifying
    @Query("update Issue i set i.githubIssueNumber = null, i.githubIssueTitle = null, "
            + "i.githubIssueUrl = null, i.githubIssueRepository = null where i.projectId = :projectId")
    int clearGitHubLinksByProjectId(@Param("projectId") UUID projectId);
}