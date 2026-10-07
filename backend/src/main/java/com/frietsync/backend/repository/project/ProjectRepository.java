package com.frietsync.backend.repository.project;

import com.frietsync.backend.entity.project.Project;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ProjectRepository extends JpaRepository<Project, UUID> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select p from Project p where p.id = :projectId")
    Optional<Project> findByIdForUpdate(@Param("projectId") UUID projectId);

    @Query("select p from Project p where p.createdBy = :userId or p.adminId = :userId "
            + "or p.projectManagerId = :userId or p.teamLeadId = :userId "
            + "or p.id in (select m.projectId from ProjectMember m where m.userId = :userId) "
            + "order by p.createdAt desc")
    List<Project> findAllForUser(@Param("userId") UUID userId);
}
