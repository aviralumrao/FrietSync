package com.frietsync.backend.service.impl.call;

import com.frietsync.backend.entity.call.CallSession;
import com.frietsync.backend.entity.call.CallType;
import com.frietsync.backend.repository.call.CallSessionRepository;
import com.frietsync.backend.service.call.JanusService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class CallRoomCleanupService {

    private final CallSessionRepository callSessionRepository;
    private final JanusService janusService;

    public void cleanupEndedCall(UUID callId, long roomId, CallType type) {
        try {
            janusService.destroyRoom(roomId, type);
            callSessionRepository.markJanusRoomDestroyed(callId);
        } catch (RuntimeException exception) {
            log.error("Could not clean up Janus room {} for call {}; cleanup will be retried",
                    roomId, callId, exception);
        }
    }

    @Scheduled(fixedDelayString = "${app.call.cleanup.delay-ms:30000}")
    public void retryPendingCleanup() {
        for (CallSession call : callSessionRepository
                .findTop50ByStatusAndJanusRoomDestroyedFalseOrderByEndedAtAsc(CallSession.CallStatus.ENDED)) {
            cleanupEndedCall(call.getId(), call.getRoomId(), call.getType());
        }
    }
}