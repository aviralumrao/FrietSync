package com.frietsync.backend.repository.project;

import com.frietsync.backend.entity.project.Label;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface LabelRepository extends JpaRepository<Label, UUID> {

    List<Label> findByProjectIdOrderByNameAsc(UUID projectId);

    boolean existsByProjectIdAndNameIgnoreCase(UUID projectId, String name);
}