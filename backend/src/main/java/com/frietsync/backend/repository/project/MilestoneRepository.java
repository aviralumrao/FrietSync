package com.frietsync.backend.repository.project;

import com.frietsync.backend.entity.project.Milestone;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface MilestoneRepository extends JpaRepository<Milestone, UUID> {

    List<Milestone> findByProjectIdOrderByDueDateAsc(UUID projectId);

    List<Milestone> findByProjectIdAndDeletedAtIsNullOrderByDueDateAsc(UUID projectId);

    void deleteByProjectId(UUID projectId);
}
