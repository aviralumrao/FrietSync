package com.frietsync.backend.repository.project;

import com.frietsync.backend.entity.project.ProjectGitHubRepository;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;
import java.util.UUID;

public interface ProjectGitHubRepositoryRepository extends JpaRepository<ProjectGitHubRepository, UUID> {
    Optional<ProjectGitHubRepository> findByProjectId(UUID projectId);
}
