package com.frietsync.backend.dto.invite;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.util.UUID;

@Data
public class InviteActionRequest {

    @NotNull
    private UUID inviteId;
}
