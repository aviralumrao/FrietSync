package com.frietsync.backend.repository.issue;

import com.frietsync.backend.entity.issue.IssueLabel;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Collection;
import java.util.UUID;

public interface IssueLabelRepository extends JpaRepository<IssueLabel, UUID> {

    List<IssueLabel> findByIssueId(UUID issueId);

    List<IssueLabel> findByIssueIdIn(Collection<UUID> issueIds);

    boolean existsByIssueIdAndLabelId(UUID issueId, UUID labelId);

    void deleteByIssueId(UUID issueId);

    void deleteByIssueIdAndLabelId(UUID issueId, UUID labelId);
}
