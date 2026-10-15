package com.frietsync.backend.dto.poll;

import com.frietsync.backend.entity.poll.PollMode;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;
import lombok.Data;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Data
public class CreatePollRequest {
    @NotBlank @Size(max = 200)
    private String question;
    @Size(max = 400)
    private String description;
    @NotEmpty @Size(min = 2, max = 20)
    private List<@NotBlank @Size(max = 300) String> options;
    private Instant endsAt;
    private PollMode mode;
    private UUID issueId;
    private UUID sprintId;
    private UUID milestoneId;
}
