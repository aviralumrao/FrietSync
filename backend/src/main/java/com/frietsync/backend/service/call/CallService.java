package com.frietsync.backend.service.call;

import com.frietsync.backend.entity.call.CallSession;

import java.util.UUID;

public interface CallService {

    CallSession startCall(UUID projectId, UUID userId);

    CallSession getActiveCall(UUID projectId, UUID userId);

    CallSession joinCall(UUID projectId, UUID callId, UUID userId);

    void leaveCall(UUID projectId, UUID callId, UUID userId);

    CallSession endCall(UUID projectId, UUID callId, UUID userId);
}
