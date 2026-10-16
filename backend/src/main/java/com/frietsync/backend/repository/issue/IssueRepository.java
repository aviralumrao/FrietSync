package com.frietsync.backend.repository.issue;

import com.frietsync.backend.entity.issue.Issue;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface IssueRepository extends JpaRepository<Issue, UUID> {

    Page<Issue> findByProjectIdAndDeletedAtIsNull(UUID projectId, Pageable pageable);

    List<Issue> findByProjectIdAndDeletedAtIsNull(UUID projectId);

    List<Issue> findByProjectIdAndDeletedAtIsNullOrderByCreatedAtAsc(UUID projectId);

    List<Issue> findByProjectIdOrderByCreatedAtDesc(UUID projectId);

    List<Issue> findByProjectId(UUID projectId);
}
