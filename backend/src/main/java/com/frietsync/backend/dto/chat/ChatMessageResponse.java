package com.frietsync.backend.dto.chat;

import com.frietsync.backend.entity.chat.ChatMessage;

import java.time.Instant;
import java.util.UUID;

public record ChatMessageResponse(
        UUID id,
        UUID projectId,
        UUID senderId,
        String senderName,
        String content,
        Instant createdAt
) {

    public static ChatMessageResponse fromEntity(ChatMessage message) {
        return new ChatMessageResponse(
                message.getId(),
                message.getProjectId(),
                message.getSenderId(),
                message.getSenderName(),
                message.getContent(),
                message.getCreatedAt()
        );
    }
}
