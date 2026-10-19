package com.frietsync.backend.service.call;

public interface JanusService {

    long createRoom();

    void destroyRoom(long roomId);
}