package com.frietsync.backend.repository.community;

import com.frietsync.backend.entity.community.CommunityComment;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.UUID;

public interface CommunityCommentRepository extends JpaRepository<CommunityComment, UUID> {

    List<CommunityComment> findByPostId(UUID postId, Pageable pageable);

    long countByPostId(UUID postId);

    @Modifying
    @Query("delete from CommunityComment c where c.postId = :postId")
    void deleteByPostId(@Param("postId") UUID postId);

    @Modifying
    @Query("delete from CommunityComment c where c.workspaceId = :workspaceId")
    void deleteByWorkspaceId(@Param("workspaceId") UUID workspaceId);
}