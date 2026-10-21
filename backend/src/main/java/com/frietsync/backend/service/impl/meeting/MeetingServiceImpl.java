package com.frietsync.backend.service.impl.meeting;

import com.frietsync.backend.dto.meeting.CreateMeetingRequest;
import com.frietsync.backend.dto.meeting.MeetingAttendeeResponse;
import com.frietsync.backend.dto.meeting.MeetingResponse;
import com.frietsync.backend.dto.meeting.UpdateMeetingRequest;
import com.frietsync.backend.entity.meeting.Meeting;
import com.frietsync.backend.entity.meeting.MeetingAttendee;
import com.frietsync.backend.entity.project.Project;
import com.frietsync.backend.entity.user.Role;
import com.frietsync.backend.entity.user.User;
import com.frietsync.backend.exception.BadRequestException;
import com.frietsync.backend.exception.ForbiddenException;
import com.frietsync.backend.exception.ResourceNotFoundException;
import com.frietsync.backend.repository.meeting.MeetingAttendeeRepository;
import com.frietsync.backend.repository.meeting.MeetingRepository;
import com.frietsync.backend.repository.project.ProjectMemberRepository;
import com.frietsync.backend.repository.project.ProjectRepository;
import com.frietsync.backend.repository.user.UserRepository;
import com.frietsync.backend.service.meeting.MeetingService;
import com.frietsync.backend.service.integration.GoogleIntegrationService;
import com.frietsync.backend.service.impl.project.ProjectAccess;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class MeetingServiceImpl implements MeetingService {

    private final MeetingRepository meetingRepository;
    private final MeetingAttendeeRepository attendeeRepository;
    private final ProjectRepository projectRepository;
    private final ProjectMemberRepository projectMemberRepository;
    private final UserRepository userRepository;
    private final GoogleIntegrationService googleIntegrationService;
    private final ProjectAccess projectAccess;

    @Override
    @Transactional
    public MeetingResponse create(UUID projectId, UUID organizerId, CreateMeetingRequest request) {
        Project project = findProject(projectId);
        projectAccess.requireWorkspaceAndActive(project, organizerId);
        if (!isProjectParticipant(project, organizerId)) {
            throw new ForbiddenException("Only project participants can schedule meetings");
        }
        validateMeetingTimes(request.getStartAt(), request.getEndAt());
        List<User> attendees = resolveProjectAttendees(project, request.getAttendeeEmails());

        Meeting meeting = new Meeting();
        meeting.setProjectId(projectId);
        meeting.setOrganizerId(organizerId);
        meeting.setTitle(request.getTitle().trim());
        meeting.setAgenda(request.getAgenda().trim());
        meeting.setStartAt(request.getStartAt());
        meeting.setEndAt(request.getEndAt());
        if (googleIntegrationService.isConnected(organizerId)) {
            List<String> attendeeEmails = new ArrayList<>();
            for (User attendee : attendees) {
                attendeeEmails.add(attendee.getEmail());
            }
            GoogleIntegrationService.GoogleMeetEvent googleMeetEvent = googleIntegrationService.createMeetEvent(organizerId,
                    meeting.getTitle(), meeting.getAgenda(), meeting.getStartAt(), meeting.getEndAt(), attendeeEmails);
            meeting.setGoogleMeetLink(googleMeetEvent.meetLink());
            meeting.setGoogleCalendarEventId(googleMeetEvent.calendarEventId());
        }
        Meeting savedMeeting = meetingRepository.save(meeting);
        for (User attendee : attendees) {
            attendeeRepository.save(newAttendee(savedMeeting.getId(), attendee.getId()));
        }
        return toResponse(savedMeeting);
    }

    @Override
    @Transactional(readOnly = true)
    public List<MeetingResponse> getMyMeetings(UUID userId) {
        UUID workspaceId = projectAccess.requireActiveWorkspace(userId);
        Map<UUID, Meeting> meetings = new HashMap<>();
        for (Meeting meeting : meetingRepository.findByOrganizerIdOrderByStartAtAsc(userId)) {
            meetings.put(meeting.getId(), meeting);
        }

        List<UUID> invitedMeetingIds = new ArrayList<>();
        for (MeetingAttendee attendee : attendeeRepository.findByUserId(userId)) {
            invitedMeetingIds.add(attendee.getMeetingId());
        }
        if (!invitedMeetingIds.isEmpty()) {
            for (Meeting meeting : meetingRepository.findByIdInOrderByStartAtAsc(invitedMeetingIds)) {
                meetings.put(meeting.getId(), meeting);
            }
        }

        List<Meeting> orderedMeetings = new ArrayList<>();
        for (Meeting meeting : meetings.values()) {
            Project project = findProject(meeting.getProjectId());
            if (workspaceId.equals(project.getWorkspaceId())
                    && isProjectParticipant(project, userId)) {
                orderedMeetings.add(meeting);
            }
        }
        orderedMeetings.sort(Comparator.comparing(Meeting::getStartAt));
        List<MeetingResponse> responses = new ArrayList<>();
        for (Meeting meeting : orderedMeetings) {
            responses.add(toResponse(meeting));
        }
        return responses;
    }

    @Override
    @Transactional(readOnly = true)
    public List<MeetingResponse> getProjectMeetings(UUID projectId, UUID userId) {
        Project project = findProject(projectId);
        projectAccess.requireWorkspaceAndActive(project, userId);
        if (!isProjectParticipant(project, userId)) {
            throw new ForbiddenException("You don't have access to this project's meetings");
        }

        List<MeetingResponse> responses = new ArrayList<>();
        for (Meeting meeting : meetingRepository.findByProjectIdOrderByStartAtAsc(projectId)) {
            responses.add(toResponse(meeting));
        }
        return responses;
    }

    @Override
    @Transactional
    public MeetingResponse update(UUID meetingId, UUID userId, UpdateMeetingRequest request) {
        Meeting meeting = findMeeting(meetingId);
        Project project = findProject(meeting.getProjectId());
        projectAccess.requireWorkspaceAndActive(project, userId);
        if (!isProjectManager(project, userId)) {
            throw new ForbiddenException("Only the project manager can edit a meeting");
        }

        if (request.getTitle() != null) {
            if (!StringUtils.hasText(request.getTitle()) || request.getTitle().length() > 200) {
                throw new BadRequestException("Meeting title must contain 1 to 200 characters");
            }
            meeting.setTitle(request.getTitle().trim());
        }
        if (request.getAgenda() != null) {
            if (!StringUtils.hasText(request.getAgenda()) || request.getAgenda().length() > 4000) {
                throw new BadRequestException("Meeting agenda must contain 1 to 4000 characters");
            }
            meeting.setAgenda(request.getAgenda().trim());
        }

        Instant startAt = request.getStartAt() == null ? meeting.getStartAt() : request.getStartAt();
        Instant endAt = request.getEndAt() == null ? meeting.getEndAt() : request.getEndAt();
        validateMeetingTimes(startAt, endAt);
        meeting.setStartAt(startAt);
        meeting.setEndAt(endAt);

        if (request.getAttendeeEmails() != null) {
            List<User> attendees = resolveProjectAttendees(project, request.getAttendeeEmails());
            attendeeRepository.deleteByMeetingId(meetingId);
            for (User attendee : attendees) {
                attendeeRepository.save(newAttendee(meetingId, attendee.getId()));
            }
        }
        return toResponse(meetingRepository.save(meeting));
    }

    @Override
    @Transactional
    public void cancel(UUID meetingId, UUID userId) {
        Meeting meeting = findMeeting(meetingId);
        Project project = findProject(meeting.getProjectId());
        projectAccess.requireWorkspaceAndActive(project, userId);
        if (!meeting.getOrganizerId().equals(userId)
                && !isProjectAdmin(project, userId)
                && !isProjectManager(project, userId)) {
            throw new ForbiddenException("Only the organizer, project admin, or project manager can cancel a meeting");
        }
        if (meeting.getGoogleCalendarEventId() != null) {
            googleIntegrationService.deleteCalendarEvent(meeting.getOrganizerId(), meeting.getGoogleCalendarEventId());
        }
        attendeeRepository.deleteByMeetingId(meetingId);
        meetingRepository.delete(meeting);
    }

    private MeetingAttendee newAttendee(UUID meetingId, UUID userId) {
        MeetingAttendee attendee = new MeetingAttendee();
        attendee.setMeetingId(meetingId);
        attendee.setUserId(userId);
        return attendee;
    }

    private MeetingResponse toResponse(Meeting meeting) {
        List<MeetingAttendeeResponse> attendees = new ArrayList<>();
        Project project = findProject(meeting.getProjectId());
        for (MeetingAttendee attendee : attendeeRepository.findByMeetingId(meeting.getId())) {
            userRepository.findById(attendee.getUserId())
                    .filter(user -> project.getWorkspaceId() != null
                            && project.getWorkspaceId().equals(user.getWorkspaceId()))
                    .ifPresent(user -> attendees.add(MeetingAttendeeResponse.fromEntity(attendee)));
        }
        return MeetingResponse.fromEntity(meeting, attendees);
    }

    private Meeting findMeeting(UUID meetingId) {
        return meetingRepository.findById(meetingId)
                .orElseThrow(() -> new ResourceNotFoundException("Meeting not found"));
    }

    private Project findProject(UUID projectId) {
        return projectRepository.findById(projectId)
                .orElseThrow(() -> new ResourceNotFoundException("Project not found"));
    }

    private void validateMeetingTimes(Instant startAt, Instant endAt) {
        if (startAt == null || endAt == null || !endAt.isAfter(startAt)) {
            throw new BadRequestException("Meeting end time must be after its start time");
        }
    }

    private List<User> resolveProjectAttendees(Project project, List<String> attendeeEmails) {
        if (attendeeEmails == null) {
            throw new BadRequestException("Attendee emails are required");
        }
        List<User> attendees = new ArrayList<>();
        List<String> normalizedEmails = new ArrayList<>();
        for (String email : attendeeEmails) {
            if (email == null || email.isBlank()) {
                throw new BadRequestException("Attendee emails must be valid email addresses");
            }
            String normalizedEmail = email.trim().toLowerCase(Locale.ROOT);
            if (normalizedEmails.contains(normalizedEmail)) {
                continue;
            }
            normalizedEmails.add(normalizedEmail);
            User attendee = userRepository.findByEmailIgnoreCase(email.trim())
                    .filter(user -> isProjectParticipant(project, user.getId()))
                    .orElseThrow(() -> new BadRequestException(
                            "Every attendee email must belong to an existing project participant"));
            projectAccess.requireWorkspaceAndActive(project, attendee.getId());
            attendees.add(attendee);
        }
        return attendees;
    }

    private boolean isProjectParticipant(Project project, UUID userId) {
        if (userId == null) {
            return false;
        }
        if (userId.equals(project.getCreatedBy())
                || userId.equals(project.getAdminId())
                || userId.equals(project.getProjectManagerId())
                || userId.equals(project.getTeamLeadId())) {
            return true;
        }
        return projectMemberRepository.existsByProjectIdAndUserId(project.getId(), userId);
    }

    private boolean isProjectAdmin(Project project, UUID userId) {
        return userId != null && userId.equals(project.getAdminId());
    }

    private boolean isProjectManager(Project project, UUID userId) {
        if (userId == null || !userId.equals(project.getProjectManagerId())) {
            return false;
        }
        User user = userRepository.findById(userId).orElse(null);
        return user != null && user.getRole() == Role.PROJECT_MANAGER;
    }
}
