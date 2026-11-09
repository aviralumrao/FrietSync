package com.frietsync.backend.dto.thread;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.util.UUID;

@Data
public class CreateCommentRequest {

    @NotBlank
    @Size(max = 2000)
    private String content;

    private UUID parentCommentId;
}
