package com.frietsync.backend.repository.workspace;

import com.frietsync.backend.entity.workspace.Workspace;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;
import java.util.UUID;

public interface WorkspaceRepository extends JpaRepository<Workspace, UUID> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT w FROM Workspace w WHERE w.id = :workspaceId")
    Optional<Workspace> findByIdForUpdate(
            @Param("workspaceId") UUID workspaceId
    );
}