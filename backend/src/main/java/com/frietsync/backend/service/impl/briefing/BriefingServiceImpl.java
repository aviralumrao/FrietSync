package com.frietsync.backend.service.impl.briefing;

import com.frietsync.backend.dto.activity.ActivityResponse;
import com.frietsync.backend.dto.briefing.BriefingMeetingResponse;
import com.frietsync.backend.dto.briefing.BriefingPreferenceResponse;
import com.frietsync.backend.dto.briefing.DailyBriefingResponse;
import com.frietsync.backend.dto.briefing.UpdateBriefingPreferenceRequest;
import com.frietsync.backend.dto.issue.IssueSummaryResponse;
import com.frietsync.backend.entity.briefing.BriefingPreference;
import com.frietsync.backend.entity.issue.Issue;
import com.frietsync.backend.entity.issue.IssueAssignee;
import com.frietsync.backend.entity.issue.IssueHistory;
import com.frietsync.backend.entity.issue.IssueHistoryAction;
import com.frietsync.backend.entity.issue.IssueStatus;
import com.frietsync.backend.entity.meeting.Meeting;
import com.frietsync.backend.entity.meeting.MeetingAttendee;
import com.frietsync.backend.entity.project.Project;
import com.frietsync.backend.entity.project.ProjectStatus;
import com.frietsync.backend.entity.sprint.Sprint;
import com.frietsync.backend.entity.sprint.SprintStatus;
import com.frietsync.backend.entity.user.User;
import com.frietsync.backend.exception.BadRequestException;
import com.frietsync.backend.repository.briefing.BriefingAssigneeRepository;
import com.frietsync.backend.repository.briefing.BriefingPreferenceRepository;
import com.frietsync.backend.repository.issue.IssueHistoryRepository;
import com.frietsync.backend.repository.issue.IssueRepository;
import com.frietsync.backend.repository.meeting.MeetingAttendeeRepository;
import com.frietsync.backend.repository.meeting.MeetingRepository;
import com.frietsync.backend.repository.project.ProjectRepository;
import com.frietsync.backend.repository.sprint.SprintRepository;
import com.frietsync.backend.repository.user.UserRepository;
import com.frietsync.backend.service.briefing.BriefingService;
import com.frietsync.backend.service.notification.NotificationService;
import com.frietsync.backend.service.impl.project.ProjectAccess;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.DateTimeException;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class BriefingServiceImpl implements BriefingService {

    private static final int DUE_SOON_DAYS = 3;
    private static final int MAX_ASSIGNED = 50;
    private static final int MAX_UPDATES = 10;
    private static final int UPDATE_FETCH_LIMIT = 100;

    private static final Set<IssueHistoryAction> IMPORTANT_ACTIONS = EnumSet.of(
            IssueHistoryAction.UPDATED,
            IssueHistoryAction.ASSIGNED,
            IssueHistoryAction.ASSIGNEE_REMOVED,
            IssueHistoryAction.ASSIGNMENT_APPROVED,
            IssueHistoryAction.ASSIGNMENT_REJECTED,
            IssueHistoryAction.RESOLVED,
            IssueHistoryAction.REVIEW_APPROVED,
            IssueHistoryAction.REVIEW_REJECTED);

    private static final Comparator<IssueSummaryResponse> BY_DUE_DATE = (a, b) -> {
        if (a.getDueDate() == null && b.getDueDate() == null) {
            return 0;
        }
        if (a.getDueDate() == null) {
            return 1;
        }
        if (b.getDueDate() == null) {
            return -1;
        }
        return a.getDueDate().compareTo(b.getDueDate());
    };

    private final IssueRepository issueRepository;
    private final BriefingAssigneeRepository assigneeRepository;
    private final IssueHistoryRepository historyRepository;
    private final BriefingPreferenceRepository preferenceRepository;
    private final UserRepository userRepository;
    private final NotificationService notificationService;
    private final MeetingRepository meetingRepository;
    private final MeetingAttendeeRepository meetingAttendeeRepository;
    private final ProjectRepository projectRepository;
    private final SprintRepository sprintRepository;
    private final ProjectAccess projectAccess;

    @Override
    @Transactional(readOnly = true)
    public DailyBriefingResponse today(UUID userId) {
        UUID workspaceId = projectAccess.requireActiveWorkspace(userId);
        ZoneId zone = zoneOf(userId);
        return build(userId, LocalDate.now(zone), zone, workspaceId);
    }

    @Override
    @Transactional(readOnly = true)
    public DailyBriefingResponse forDate(UUID userId, LocalDate date) {
        UUID workspaceId = projectAccess.requireActiveWorkspace(userId);
        ZoneId zone = zoneOf(userId);
        if (date.isAfter(LocalDate.now(zone))) {
            throw new BadRequestException("Date is in the future");
        }
        return build(userId, date, zone, workspaceId);
    }

    @Override
    @Transactional(readOnly = true)
    public BriefingPreferenceResponse getPreferences(UUID userId) {
        BriefingPreference preference = preferenceRepository.findByUserId(userId).orElse(null);
        if (preference == null) {
            preference = new BriefingPreference();
        }
        return toResponse(preference);
    }

    @Override
    @Transactional
    public BriefingPreferenceResponse updatePreferences(UUID userId, UpdateBriefingPreferenceRequest request) {
        if (request.getDeliveryTime() == null && request.getTimeZone() == null
                && request.getEmailEnabled() == null) {
            throw new BadRequestException("Nothing to update");
        }

        BriefingPreference preference = preferenceRepository.findByUserId(userId).orElse(null);
        if (preference == null) {
            preference = new BriefingPreference();
            preference.setUserId(userId);
        }

        if (request.getTimeZone() != null) {
            try {
                ZoneId.of(request.getTimeZone());
            } catch (DateTimeException ex) {
                throw new BadRequestException("Unknown time zone");
            }
            preference.setTimeZone(request.getTimeZone());
        }
        if (request.getDeliveryTime() != null) {
            preference.setDeliveryTime(request.getDeliveryTime().withSecond(0).withNano(0));
        }
        if (request.getEmailEnabled() != null) {
            preference.setEmailEnabled(request.getEmailEnabled());
        }

        ZonedDateTime now = ZonedDateTime.now(ZoneId.of(preference.getTimeZone()));
        if (preference.isEmailEnabled() && !now.toLocalTime().isBefore(preference.getDeliveryTime())) {
            preference.setLastSentOn(now.toLocalDate());
        }

        preference = preferenceRepository.save(preference);
        return toResponse(preference);
    }

    private DailyBriefingResponse build(UUID userId, LocalDate date, ZoneId zone, UUID workspaceId) {
        boolean isToday = date.equals(LocalDate.now(zone));
        Set<UUID> workspaceProjectIds = new HashSet<>();
        projectRepository.findAllForUser(userId).stream()
                .filter(project -> workspaceId.equals(project.getWorkspaceId()))
                .forEach(project -> workspaceProjectIds.add(project.getId()));

        Instant dayStart = date.atStartOfDay(zone).toInstant();
        Instant dayEnd = date.plusDays(1).atStartOfDay(zone).toInstant().minusSeconds(1);
        Instant moment = dayEnd;
        if (isToday) {
            moment = Instant.now();
        }
        Instant newSince = moment.minus(Duration.ofHours(24));
        LocalDate yesterday = date.minusDays(1);

        List<UUID> issueIds = new ArrayList<>();
        for (IssueAssignee assignee : assigneeRepository.findByUserId(userId)) {
            if (!assignee.getCreatedAt().isAfter(moment)) {
                issueIds.add(assignee.getIssueId());
            }
        }
        List<Issue> issues = issueRepository.findAllById(issueIds);
        issues.removeIf(issue -> !workspaceProjectIds.contains(issue.getProjectId()));

        List<IssueSummaryResponse> overdue = new ArrayList<>();
        List<IssueSummaryResponse> dueToday = new ArrayList<>();
        List<IssueSummaryResponse> dueSoon = new ArrayList<>();
        List<IssueSummaryResponse> completedYesterday = new ArrayList<>();
        List<IssueSummaryResponse> newIssues = new ArrayList<>();
        List<IssueSummaryResponse> assigned = new ArrayList<>();
        Map<UUID, Issue> myIssues = new LinkedHashMap<>();
        Set<UUID> openProjectIds = new HashSet<>();
        int open = 0;
        int completed = 0;
        LocalDate soonLimit = date.plusDays(DUE_SOON_DAYS);

        for (Issue issue : issues) {
            if (issue.getDeletedAt() != null || issue.getCreatedAt().isAfter(moment)) {
                continue;
            }
            myIssues.put(issue.getId(), issue);

            Instant finishedAt = finishedAt(issue);
            if (finishedAt != null && !finishedAt.isAfter(moment)) {
                LocalDate finishedDay = finishedAt.atZone(zone).toLocalDate();
                if (finishedDay.equals(date)) {
                    completed++;
                } else if (finishedDay.equals(yesterday)) {
                    completedYesterday.add(toItem(issue, issue.getStatus()));
                }
                continue;
            }

            open++;
            openProjectIds.add(issue.getProjectId());
            IssueStatus shownStatus = IssueStatus.OPEN;
            if (isToday) {
                shownStatus = issue.getStatus();
            }
            IssueSummaryResponse item = toItem(issue, shownStatus);
            assigned.add(item);
            if (!issue.getCreatedAt().isBefore(newSince)) {
                newIssues.add(item);
            }

            LocalDate due = issue.getDueDate();
            if (due == null) {
                continue;
            }
            if (due.isBefore(date)) {
                overdue.add(item);
            } else if (due.equals(date)) {
                dueToday.add(item);
            } else if (!due.isAfter(soonLimit)) {
                dueSoon.add(item);
            }
        }

        overdue.sort(BY_DUE_DATE);
        dueSoon.sort(BY_DUE_DATE);
        assigned.sort(BY_DUE_DATE);
        if (assigned.size() > MAX_ASSIGNED) {
            assigned = new ArrayList<>(assigned.subList(0, MAX_ASSIGNED));
        }

        Instant meetingsFrom = dayStart;
        if (isToday) {
            meetingsFrom = moment;
        }
        List<BriefingMeetingResponse> meetings = meetingsFor(userId, meetingsFrom, dayEnd, workspaceProjectIds);
        List<ActivityResponse> updates = projectUpdates(userId, myIssues, newSince, moment);

        List<String> alerts = new ArrayList<>();
        if (isToday) {
            alerts = projectAlerts(openProjectIds, date);
        }

        String userName = null;
        User user = userRepository.findById(userId).orElse(null);
        if (user != null) {
            userName = user.getName();
        }

        Long unread = null;
        if (isToday) {
            unread = notificationService.unreadCount(userId);
        }

        return DailyBriefingResponse.builder()
                .date(date)
                .timeZone(zone.getId())
                .userName(userName)
                .headline(headline(overdue.size(), dueToday.size(), dueSoon.size(), meetings.size()))
                .openIssues(open)
                .completedIssues(completed)
                .completedYesterday(completedYesterday)
                .overdue(overdue)
                .dueToday(dueToday)
                .dueSoon(dueSoon)
                .newIssues(newIssues)
                .assignedTasks(assigned)
                .upcomingMeetings(meetings)
                .projectUpdates(updates)
                .projectAlerts(alerts)
                .unreadNotifications(unread)
                .build();
    }

    private List<BriefingMeetingResponse> meetingsFor(
            UUID userId, Instant from, Instant to, Set<UUID> workspaceProjectIds
    ) {
        Map<UUID, Meeting> found = new HashMap<>();
        for (Meeting meeting : meetingRepository.findByOrganizerIdOrderByStartAtAsc(userId)) {
            found.put(meeting.getId(), meeting);
        }
        List<UUID> invitedIds = new ArrayList<>();
        for (MeetingAttendee attendee : meetingAttendeeRepository.findByUserId(userId)) {
            invitedIds.add(attendee.getMeetingId());
        }
        if (!invitedIds.isEmpty()) {
            for (Meeting meeting : meetingRepository.findByIdInOrderByStartAtAsc(invitedIds)) {
                found.put(meeting.getId(), meeting);
            }
        }

        List<Meeting> inWindow = new ArrayList<>();
        for (Meeting meeting : found.values()) {
            if (!workspaceProjectIds.contains(meeting.getProjectId())) {
                continue;
            }
            Instant start = meeting.getStartAt();
            if (start == null) {
                continue;
            }
            Instant end = meeting.getEndAt();
            if (end == null) {
                end = start;
            }
            if (!start.isAfter(to) && end.isAfter(from)) {
                inWindow.add(meeting);
            }
        }
        inWindow.sort(Comparator.comparing(Meeting::getStartAt));

        List<BriefingMeetingResponse> result = new ArrayList<>();
        for (Meeting meeting : inWindow) {
            result.add(BriefingMeetingResponse.builder()
                    .meetingId(meeting.getId())
                    .projectId(meeting.getProjectId())
                    .title(meeting.getTitle())
                    .agenda(meeting.getAgenda())
                    .startAt(meeting.getStartAt())
                    .endAt(meeting.getEndAt())
                    .build());
        }
        return result;
    }

    private List<ActivityResponse> projectUpdates(UUID userId, Map<UUID, Issue> myIssues, Instant from,
                                                  Instant to) {
        List<ActivityResponse> result = new ArrayList<>();
        if (myIssues.isEmpty()) {
            return result;
        }

        Set<UUID> projectIds = new HashSet<>();
        for (Issue issue : myIssues.values()) {
            projectIds.add(issue.getProjectId());
        }

        List<IssueHistory> picked = new ArrayList<>();
        for (UUID projectId : projectIds) {
            Page<IssueHistory> page = historyRepository.findAllForProject(projectId,
                    PageRequest.of(0, UPDATE_FETCH_LIMIT));
            for (IssueHistory entry : page.getContent()) {
                if (!myIssues.containsKey(entry.getIssueId())) {
                    continue;
                }
                if (userId.equals(entry.getActorId())) {
                    continue;
                }
                if (!IMPORTANT_ACTIONS.contains(entry.getAction())) {
                    continue;
                }
                Instant at = entry.getCreatedAt();
                if (at == null || at.isBefore(from) || at.isAfter(to)) {
                    continue;
                }
                picked.add(entry);
            }
        }
        picked.sort((a, b) -> b.getCreatedAt().compareTo(a.getCreatedAt()));
        if (picked.size() > MAX_UPDATES) {
            picked = new ArrayList<>(picked.subList(0, MAX_UPDATES));
        }
        if (picked.isEmpty()) {
            return result;
        }

        Set<UUID> actorIds = new HashSet<>();
        for (IssueHistory entry : picked) {
            actorIds.add(entry.getActorId());
        }
        Map<UUID, String> names = new HashMap<>();
        for (User actor : userRepository.findAllById(actorIds)) {
            names.put(actor.getId(), actor.getName());
        }

        for (IssueHistory entry : picked) {
            Issue issue = myIssues.get(entry.getIssueId());
            result.add(ActivityResponse.builder()
                    .id(entry.getId())
                    .issueId(entry.getIssueId())
                    .issueNumber(issue.getIssueNumber())
                    .issueTitle(issue.getTitle())
                    .actorId(entry.getActorId())
                    .actorName(names.get(entry.getActorId()))
                    .action(entry.getAction())
                    .fieldName(entry.getFieldName())
                    .oldValue(entry.getOldValue())
                    .newValue(entry.getNewValue())
                    .comment(entry.getComment())
                    .createdAt(entry.getCreatedAt())
                    .build());
        }
        return result;
    }

    private List<String> projectAlerts(Set<UUID> projectIds, LocalDate today) {
        List<String> alerts = new ArrayList<>();
        if (projectIds.isEmpty()) {
            return alerts;
        }
        for (Project project : projectRepository.findAllById(projectIds)) {
            if (project.getStatus() == ProjectStatus.COMPLETED || project.getStatus() == ProjectStatus.CANCELLED) {
                continue;
            }
            LocalDate deadline = project.getDeadline();
            if (deadline != null) {
                long days = ChronoUnit.DAYS.between(today, deadline);
                if (days < 0) {
                    alerts.add(project.getName() + " is " + (-days) + " day(s) past its deadline");
                } else if (days == 0) {
                    alerts.add(project.getName() + " has its deadline today");
                } else if (days <= DUE_SOON_DAYS) {
                    alerts.add(project.getName() + " has its deadline in " + days + " day(s)");
                }
            }
            for (Sprint sprint : sprintRepository.findByProjectId(project.getId())) {
                if (sprint.getStatus() != SprintStatus.ACTIVE || sprint.getEndDate() == null) {
                    continue;
                }
                long days = ChronoUnit.DAYS.between(today, sprint.getEndDate());
                String label = "Sprint " + sprint.getName() + " in " + project.getName();
                if (days < 0) {
                    alerts.add(label + " is " + (-days) + " day(s) past its end date");
                } else if (days == 0) {
                    alerts.add(label + " ends today");
                } else if (days <= DUE_SOON_DAYS) {
                    alerts.add(label + " ends in " + days + " day(s)");
                }
            }
        }
        return alerts;
    }

    private String headline(int overdue, int dueToday, int dueSoon, int meetings) {
        if (overdue + dueToday + dueSoon + meetings == 0) {
            return "Nothing needs your attention.";
        }
        String text = overdue + " overdue, " + dueToday + " due today, " + dueSoon + " due soon.";
        if (meetings > 0) {
            String noun = " meetings";
            if (meetings == 1) {
                noun = " meeting";
            }
            text = text + " " + meetings + noun + " coming up.";
        }
        return text;
    }

    private ZoneId zoneOf(UUID userId) {
        BriefingPreference preference = preferenceRepository.findByUserId(userId).orElse(null);
        if (preference == null) {
            return ZoneId.of("UTC");
        }
        return ZoneId.of(preference.getTimeZone());
    }

    private Instant finishedAt(Issue issue) {
        if (issue.getStatus() != IssueStatus.RESOLVED && issue.getStatus() != IssueStatus.CLOSED) {
            return null;
        }
        if (issue.getResolvedAt() != null) {
            return issue.getResolvedAt();
        }
        return issue.getClosedAt();
    }

    private IssueSummaryResponse toItem(Issue issue, IssueStatus status) {
        return IssueSummaryResponse.builder()
                .id(issue.getId())
                .issueNumber(issue.getIssueNumber())
                .title(issue.getTitle())
                .status(status)
                .priority(issue.getPriority())
                .dueDate(issue.getDueDate())
                .build();
    }

    private BriefingPreferenceResponse toResponse(BriefingPreference preference) {
        return BriefingPreferenceResponse.builder()
                .deliveryTime(preference.getDeliveryTime())
                .timeZone(preference.getTimeZone())
                .emailEnabled(preference.isEmailEnabled())
                .build();
    }
}