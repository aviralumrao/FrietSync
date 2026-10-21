package com.frietsync.backend.repository.issue;

import com.frietsync.backend.entity.issue.IssueHistory;
import com.frietsync.backend.entity.issue.IssueHistoryAction;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

public interface IssueHistoryRepository extends JpaRepository<IssueHistory, UUID> {

    Page<IssueHistory> findByIssueIdOrderByCreatedAtDescIdDesc(UUID issueId, Pageable pageable);

    void deleteByIssueId(UUID issueId);

    @Query("select h from IssueHistory h where h.issueId in "
            + "(select i.id from Issue i where i.projectId = :projectId) "
            + "order by h.createdAt desc, h.id desc")
    Page<IssueHistory> findAllForProject(@Param("projectId") UUID projectId, Pageable pageable);

    @Query("select h from IssueHistory h where h.action = :action and h.issueId in "
            + "(select i.id from Issue i where i.projectId = :projectId) "
            + "order by h.createdAt desc, h.id desc")
    Page<IssueHistory> findAllForProjectByAction(@Param("projectId") UUID projectId,
                                                 @Param("action") IssueHistoryAction action,
                                                 Pageable pageable);

    @Query("select distinct h.issueId from IssueHistory h where h.issueId in :issueIds and "
            + "(h.action = com.frietsync.backend.entity.issue.IssueHistoryAction.REVIEW_REJECTED "
            + "or (h.action = com.frietsync.backend.entity.issue.IssueHistoryAction.UPDATED "
            + "and h.fieldName = 'status' and h.newValue = 'REOPENED'))")
    List<UUID> findReopenedIssueIds(@Param("issueIds") Collection<UUID> issueIds);

    long countByActorIdAndAction(UUID actorId, IssueHistoryAction action);

    @Query("select count(h) from IssueHistory h where h.actorId = :actorId and h.action = :action "
            + "and h.issueId in (select i.id from Issue i where i.projectId in :projectIds)")
    long countByActorAndActionInProjects(@Param("actorId") UUID actorId,
                                         @Param("action") IssueHistoryAction action,
                                         @Param("projectIds") Collection<UUID> projectIds);
}