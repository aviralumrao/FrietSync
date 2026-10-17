package com.frietsync.backend.repository.issue;

import com.frietsync.backend.entity.issue.IssueHistory;
import com.frietsync.backend.entity.issue.IssueHistoryAction;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

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
}