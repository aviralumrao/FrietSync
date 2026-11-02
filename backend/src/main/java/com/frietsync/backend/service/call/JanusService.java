package com.frietsync.backend.service.call;

import com.frietsync.backend.entity.call.CallType;

public interface JanusService {

    long createRoom(CallType type);

    void destroyRoom(long roomId, CallType type);
}