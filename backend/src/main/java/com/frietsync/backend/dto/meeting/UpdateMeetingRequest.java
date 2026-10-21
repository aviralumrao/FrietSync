package com.frietsync.backend.dto.meeting;

import jakarta.validation.constraints.Size;
import jakarta.validation.constraints.Email;
import lombok.Data;

import java.time.Instant;
import java.util.List;

@Data
public class UpdateMeetingRequest {

    @Size(min = 1, max = 200)
    private String title;

    @Size(min = 1, max = 4000)
    private String agenda;

    private Instant startAt;

    private Instant endAt;

    private List<@Email String> attendeeEmails;
}
