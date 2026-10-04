package com.frietsync.backend.repository.project;

import com.frietsync.backend.entity.project.Project;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.UUID;

public interface ProjectRepository extends JpaRepository<Project, UUID> {

    @Query("select p from Project p where p.createdBy = :userId or p.adminId = :userId "
            + "or p.projectManagerId = :userId or p.teamLeadId = :userId "
            + "order by p.createdAt desc")
    List<Project> findAllForUser(@Param("userId") UUID userId);
}
