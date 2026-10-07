package com.frietsync.backend.repository.chat;

import com.frietsync.backend.entity.chat.ChatMessage;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.UUID;

public interface ChatMessageRepository extends JpaRepository<ChatMessage, UUID> {

    List<ChatMessage> findByProjectId(UUID projectId, Pageable pageable);

    @Modifying
    @Query("delete from ChatMessage message where message.projectId = :projectId")
    void deleteByProjectId(@Param("projectId") UUID projectId);
}
