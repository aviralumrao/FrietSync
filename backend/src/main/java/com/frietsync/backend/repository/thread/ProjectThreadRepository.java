package com.frietsync.backend.repository.thread;

import com.frietsync.backend.entity.thread.ProjectThread;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface ProjectThreadRepository extends JpaRepository<ProjectThread, UUID> {

    Page<ProjectThread> findByProjectIdAndDeletedAtIsNullOrderByCreatedAtDesc(UUID projectId, Pageable pageable);
}
