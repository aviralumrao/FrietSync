package com.frietsync.backend.dto.community;

import com.frietsync.backend.entity.community.ReactionType;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class ReactionRequest {

    @NotNull
    private ReactionType type;
}