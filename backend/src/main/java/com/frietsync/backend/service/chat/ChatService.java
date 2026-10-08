package com.frietsync.backend.service.chat;

import com.frietsync.backend.dto.chat.ChatMessageRequest;
import com.frietsync.backend.dto.chat.ChatMessageResponse;

import java.util.List;
import java.util.UUID;

public interface ChatService {

    ChatMessageResponse sendMessage(UUID projectId, UUID senderId, ChatMessageRequest request);

    List<ChatMessageResponse> getRecentMessages(UUID projectId, UUID userId, int limit);

    void assertProjectAccess(UUID projectId, UUID userId);
}
