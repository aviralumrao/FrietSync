package com.frietsync.backend.repository.community;

import com.frietsync.backend.entity.community.CommunityReaction;
import com.frietsync.backend.entity.community.ReactionTargetType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface CommunityReactionRepository extends JpaRepository<CommunityReaction, UUID> {

    List<CommunityReaction> findByTargetTypeAndTargetId(ReactionTargetType targetType, UUID targetId);

    Optional<CommunityReaction> findByTargetTypeAndTargetIdAndUserId(
            ReactionTargetType targetType, UUID targetId, UUID userId);

    @Modifying
    @Query("delete from CommunityReaction r where r.targetType = :targetType and r.targetId = :targetId")
    void deleteByTarget(@Param("targetType") ReactionTargetType targetType, @Param("targetId") UUID targetId);

    @Modifying
    @Query("delete from CommunityReaction r where r.workspaceId = :workspaceId")
    void deleteByWorkspaceId(@Param("workspaceId") UUID workspaceId);
}