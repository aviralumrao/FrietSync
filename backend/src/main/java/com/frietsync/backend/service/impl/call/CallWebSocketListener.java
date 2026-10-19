package com.frietsync.backend.service.impl.call;

import com.frietsync.backend.service.call.CallEvent;
import lombok.RequiredArgsConstructor;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Component
@RequiredArgsConstructor
public class CallWebSocketListener {

    private final SimpMessagingTemplate messagingTemplate;

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
    public void publish(CallEvent event) {
        messagingTemplate.convertAndSend(
                "/topic/projects/" + event.projectId() + "/calls",
                event
        );
    }
}
