package com.frietsync.backend.dto.call;

import com.frietsync.backend.entity.call.CallType;

public record StartCallRequest(
        CallType type
) {
}