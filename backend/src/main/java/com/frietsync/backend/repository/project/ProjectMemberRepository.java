package com.frietsync.backend.repository.project;

import com.frietsync.backend.entity.project.ProjectMember;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ProjectMemberRepository extends JpaRepository<ProjectMember, UUID> {

    List<ProjectMember> findByProjectId(UUID projectId);

    Optional<ProjectMember> findByProjectIdAndUserId(UUID projectId, UUID userId);

    boolean existsByProjectIdAndUserId(UUID projectId, UUID userId);

    @Query("select p.id from Project p where p.projectManagerId = :userId or p.id in "
            + "(select m.projectId from ProjectMember m where m.userId = :userId "
            + "and m.role = com.frietsync.backend.entity.user.Role.PROJECT_MANAGER)")
    List<UUID> findManagedProjectIds(@Param("userId") UUID userId);

    @Query("select p.id from Project p where p.projectManagerId = :userId or p.teamLeadId = :userId "
            + "or p.id in (select m.projectId from ProjectMember m where m.userId = :userId)")
    List<UUID> findProjectIdsOfUser(@Param("userId") UUID userId);
}