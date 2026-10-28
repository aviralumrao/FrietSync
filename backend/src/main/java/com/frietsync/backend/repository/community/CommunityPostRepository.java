package com.frietsync.backend.repository.community;

import com.frietsync.backend.entity.community.CommunityPost;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.UUID;

public interface CommunityPostRepository extends JpaRepository<CommunityPost, UUID> {

    Page<CommunityPost> findByWorkspaceIdAndDeletedFalse(UUID workspaceId, Pageable pageable);

    List<CommunityPost> findByWorkspaceIdAndPinnedAtIsNotNullAndDeletedFalseOrderByPinnedAtDesc(UUID workspaceId);

    long countByWorkspaceIdAndPinnedAtIsNotNullAndDeletedFalse(UUID workspaceId);

    @Modifying
    @Query("delete from CommunityPost p where p.workspaceId = :workspaceId")
    void deleteByWorkspaceId(@Param("workspaceId") UUID workspaceId);
}