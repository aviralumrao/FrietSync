package com.frietsync.backend.dto.poll;

import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class PollUpdateEvent {
    private String type;
    private PollResponse poll;
}
