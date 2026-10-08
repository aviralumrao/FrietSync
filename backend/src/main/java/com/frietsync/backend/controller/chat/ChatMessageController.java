package com.frietsync.backend.controller.chat;

import com.frietsync.backend.dto.chat.ChatMessageRequest;
import com.frietsync.backend.dto.chat.ChatMessageResponse;
import com.frietsync.backend.service.chat.ChatService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.messaging.handler.annotation.DestinationVariable;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.security.core.Authentication;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@Validated
@RestController
@RequiredArgsConstructor
public class ChatMessageController {

    private final ChatService chatService;
    private final SimpMessagingTemplate messagingTemplate;

    @MessageMapping("/projects/{projectId}/chat")
    public void sendMessage(@DestinationVariable UUID projectId,
                            @Valid ChatMessageRequest request,
                            Authentication authentication) {
        UUID senderId = (UUID) authentication.getPrincipal();
        ChatMessageResponse message = chatService.sendMessage(projectId, senderId, request);
        messagingTemplate.convertAndSend("/topic/projects/" + projectId + "/chat", message);
    }
}
