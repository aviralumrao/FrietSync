package com.frietsync.backend.repository.project;

import com.frietsync.backend.entity.project.Label;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface LabelRepository extends JpaRepository<Label, UUID> {

    List<Label> findByProjectIdOrderByNameAsc(UUID projectId);

    Optional<Label> findByProjectIdAndNameIgnoreCase(UUID projectId, String name);

    boolean existsByProjectIdAndNameIgnoreCase(UUID projectId, String name);
}
