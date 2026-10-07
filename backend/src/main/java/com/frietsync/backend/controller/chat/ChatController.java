package com.frietsync.backend.controller.chat;

import com.frietsync.backend.dto.chat.ChatMessageResponse;
import com.frietsync.backend.exception.BadRequestException;
import com.frietsync.backend.service.chat.ChatService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/projects/{projectId}/chat/messages")
@RequiredArgsConstructor
public class ChatController {

    private final ChatService chatService;

    @GetMapping
    public List<ChatMessageResponse> recentMessages(
            @PathVariable UUID projectId,
            @AuthenticationPrincipal UUID userId)
        return chatService.getRecentMessages(projectId, userId, limit);
    }
}
