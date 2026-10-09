package com.frietsync.backend.service.user;

import java.util.UUID;

public interface UserService {
    void leaveWorkspace(UUID userId);
}