package com.frietsync.backend.dto.meeting;

import com.frietsync.backend.entity.meeting.Meeting;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record MeetingResponse(
        UUID id,
        UUID projectId,
        UUID organizerId,
        String title,
        String agenda,
        Instant startAt,
        Instant endAt,
        String googleMeetLink,
        List<MeetingAttendeeResponse> attendees,
        Instant createdAt,
        Instant updatedAt
) {

    public static MeetingResponse fromEntity(Meeting meeting, List<MeetingAttendeeResponse> attendees) {
        return new MeetingResponse(
                meeting.getId(),
                meeting.getProjectId(),
                meeting.getOrganizerId(),
                meeting.getTitle(),
                meeting.getAgenda(),
                meeting.getStartAt(),
                meeting.getEndAt(),
                meeting.getGoogleMeetLink(),
                attendees,
                meeting.getCreatedAt(),
                meeting.getUpdatedAt()
        );
    }
}
