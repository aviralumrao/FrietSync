package com.frietsync.backend.repository.issue;

import com.frietsync.backend.entity.issue.IssueLink;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface IssueLinkRepository extends JpaRepository<IssueLink, UUID> {

    List<IssueLink> findBySourceIssueIdOrTargetIssueIdOrderByCreatedAtDesc(UUID sourceIssueId, UUID targetIssueId);

    List<IssueLink> findByProjectId(UUID projectId);

    boolean existsBySourceIssueIdAndTargetIssueIdOrSourceIssueIdAndTargetIssueId(
            UUID sourceIssueId, UUID targetIssueId, UUID reverseSourceIssueId, UUID reverseTargetIssueId);

    void deleteBySourceIssueIdOrTargetIssueId(UUID sourceIssueId, UUID targetIssueId);
}
