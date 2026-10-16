package com.frietsync.backend.service.impl.dashboard;

import com.frietsync.backend.dto.dashboard.ProjectBoardResponse;
import com.frietsync.backend.dto.dashboard.ProjectCalendarResponse;
import com.frietsync.backend.dto.dashboard.ProjectTimelineResponse;
import com.frietsync.backend.entity.issue.Issue;
import com.frietsync.backend.entity.issue.IssueLink;
import com.frietsync.backend.entity.issue.IssueStatus;
import com.frietsync.backend.entity.meeting.Meeting;
import com.frietsync.backend.entity.poll.Poll;
import com.frietsync.backend.entity.project.Milestone;
import com.frietsync.backend.entity.project.Release;
import com.frietsync.backend.entity.sprint.Sprint;
import com.frietsync.backend.repository.issue.IssueLinkRepository;
import com.frietsync.backend.repository.issue.IssueRepository;
import com.frietsync.backend.repository.meeting.MeetingRepository;
import com.frietsync.backend.repository.poll.PollRepository;
import com.frietsync.backend.repository.project.MilestoneRepository;
import com.frietsync.backend.repository.project.ReleaseRepository;
import com.frietsync.backend.repository.sprint.SprintRepository;
import com.frietsync.backend.service.impl.project.ProjectAccess;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ProjectDashboardService {

    private final IssueRepository issueRepository;
    private final IssueLinkRepository issueLinkRepository;
    private final SprintRepository sprintRepository;
    private final MilestoneRepository milestoneRepository;
    private final ReleaseRepository releaseRepository;
    private final MeetingRepository meetingRepository;
    private final PollRepository pollRepository;
    private final ProjectAccess projectAccess;

    public ProjectBoardResponse board(UUID projectId, UUID userId) {
        requireProjectMember(projectId, userId);
        Map<IssueStatus, List<ProjectBoardResponse.IssueCard>> issuesByStatus = issueRepository
                .findByProjectIdAndDeletedAtIsNullOrderByCreatedAtAsc(projectId).stream()
                .collect(Collectors.groupingBy(Issue::getStatus, () -> new EnumMap<>(IssueStatus.class),
                        Collectors.mapping(issue -> new ProjectBoardResponse.IssueCard(issue.getId(),
                                issue.getIssueNumber(), issue.getTitle(), issue.getPriority(), issue.getDueDate()),
                                Collectors.toList())));

        List<ProjectBoardResponse.Column> columns = new ArrayList<>();
        for (IssueStatus status : IssueStatus.values()) {
            columns.add(new ProjectBoardResponse.Column(status,
                    issuesByStatus.getOrDefault(status, List.of()), allowedTransitions(status)));
        }
        return new ProjectBoardResponse(columns);
    }

    public ProjectCalendarResponse calendar(UUID projectId, UUID userId) {
        requireProjectMember(projectId, userId);
        List<ProjectCalendarResponse.Event> events = new ArrayList<>();

        for (Issue issue : issueRepository.findByProjectIdAndDeletedAtIsNull(projectId)) {
            if (issue.getDueDate() != null) {
                events.add(new ProjectCalendarResponse.Event("ISSUE_DUE_DATE", issue.getId(),
                        issue.getTitle(), issue.getDueDate(), issue.getDueDate()));
            }
        }
        for (Sprint sprint : sprintRepository.findByProjectId(projectId)) {
            if (sprint.getStartDate() != null || sprint.getEndDate() != null) {
                events.add(new ProjectCalendarResponse.Event("SPRINT", sprint.getId(), sprint.getName(),
                        sprint.getStartDate(), sprint.getEndDate()));
            }
        }
        for (Milestone milestone : milestoneRepository.findByProjectIdOrderByDueDateAsc(projectId)) {
            events.add(new ProjectCalendarResponse.Event("MILESTONE", milestone.getId(), milestone.getName(),
                    milestone.getDueDate(), milestone.getDueDate()));
        }
        for (Release release : releaseRepository.findByProjectIdOrderByReleaseDateAsc(projectId)) {
            if (release.getReleaseDate() != null) {
                events.add(new ProjectCalendarResponse.Event("RELEASE", release.getId(), release.getName(),
                        release.getReleaseDate(), release.getReleaseDate()));
            }
        }
        for (Meeting meeting : meetingRepository.findByProjectIdOrderByStartAtAsc(projectId)) {
            events.add(new ProjectCalendarResponse.Event("MEETING", meeting.getId(), meeting.getTitle(),
                    localDate(meeting.getStartAt()), localDate(meeting.getEndAt())));
        }
        for (Poll poll : pollRepository.findByProjectIdOrderByCreatedAtDesc(projectId)) {
            if (poll.getClosesAt() != null) {
                LocalDate closeDate = localDate(poll.getClosesAt());
                events.add(new ProjectCalendarResponse.Event("POLL_DEADLINE", poll.getId(), poll.getQuestion(),
                        closeDate, closeDate));
            }
        }
        events.sort(Comparator.comparing(ProjectCalendarResponse.Event::getStartDate,
                Comparator.nullsLast(Comparator.naturalOrder())));
        return new ProjectCalendarResponse(events);
    }

    public ProjectTimelineResponse timeline(UUID projectId, UUID userId) {
        requireProjectMember(projectId, userId);
        List<Issue> issues = issueRepository.findByProjectIdAndDeletedAtIsNullOrderByCreatedAtAsc(projectId);
        List<Sprint> sprints = sprintRepository.findByProjectId(projectId);
        List<Milestone> milestones = milestoneRepository.findByProjectIdOrderByDueDateAsc(projectId);
        List<ProjectTimelineResponse.Group> groups = new ArrayList<>();
        List<ProjectTimelineResponse.IssueBar> ungroupedIssues = new ArrayList<>();
        Map<String, TimelineGroupMatch> timelineGroups = new HashMap<>();

        for (Issue issue : issues) {
            ProjectTimelineResponse.IssueBar bar = toIssueBar(issue);
            TimelineGroupMatch match = findGroupForIssue(issue, sprints, milestones, timelineGroups);
            if (match == null) {
                ungroupedIssues.add(bar);
            } else {
                match.getIssues().add(bar);
            }
        }
        for (Sprint sprint : sprints) {
            List<ProjectTimelineResponse.IssueBar> bars = groupsFor(timelineGroups, "SPRINT", sprint.getId()).getIssues();
            groups.add(new ProjectTimelineResponse.Group("SPRINT", sprint.getId(), sprint.getName(),
                    sprint.getStartDate(), sprint.getEndDate(), bars));
        }
        for (Milestone milestone : milestones) {
            List<ProjectTimelineResponse.IssueBar> bars = groupsFor(timelineGroups, "MILESTONE", milestone.getId()).getIssues();
            groups.add(new ProjectTimelineResponse.Group("MILESTONE", milestone.getId(), milestone.getName(),
                    milestone.getDueDate(), milestone.getDueDate(), bars));
        }
        if (!ungroupedIssues.isEmpty()) {
            groups.add(new ProjectTimelineResponse.Group("PROJECT", projectId, "Project issues",
                    null, null, ungroupedIssues));
        }

        List<ProjectTimelineResponse.Dependency> dependencies = issueLinkRepository.findByProjectId(projectId)
                .stream().map(ProjectDashboardService::toDependency).toList();
        List<ProjectTimelineResponse.ReleaseMarker> releases = releaseRepository
                .findByProjectIdOrderByReleaseDateAsc(projectId).stream()
                .filter(release -> release.getReleaseDate() != null)
                .map(release -> new ProjectTimelineResponse.ReleaseMarker(
                        release.getId(), release.getName(), release.getReleaseDate())).toList();
        return new ProjectTimelineResponse(groups, dependencies, releases);
    }

    private TimelineGroupMatch findGroupForIssue(Issue issue, List<Sprint> sprints, List<Milestone> milestones,
                                                 Map<String, TimelineGroupMatch> timelineGroups) {
        LocalDate dueDate = issue.getDueDate();
        if (dueDate == null) {
            return null;
        }
        for (Sprint sprint : sprints) {
            if ((sprint.getStartDate() != null || sprint.getEndDate() != null)
                    && between(dueDate, sprint.getStartDate(), sprint.getEndDate())) {
                return groupsFor(timelineGroups, "SPRINT", sprint.getId());
            }
        }
        for (Milestone milestone : milestones) {
            if (milestone.getDueDate().equals(dueDate)) {
                return groupsFor(timelineGroups, "MILESTONE", milestone.getId());
            }
        }
        return null;
    }

    private TimelineGroupMatch groupsFor(Map<String, TimelineGroupMatch> timelineGroups, String type, UUID id) {
        return timelineGroups.computeIfAbsent(type + id, key -> new TimelineGroupMatch(new ArrayList<>()));
    }

    private boolean between(LocalDate date, LocalDate startDate, LocalDate endDate) {
        return (startDate == null || !date.isBefore(startDate))
                && (endDate == null || !date.isAfter(endDate));
    }

    private ProjectTimelineResponse.IssueBar toIssueBar(Issue issue) {
        LocalDate startDate = issue.getCreatedAt() == null ? null : localDate(issue.getCreatedAt());
        LocalDate dueDate = issue.getDueDate();
        boolean unfinished = issue.getStatus() != IssueStatus.CLOSED && issue.getStatus() != IssueStatus.RESOLVED;
        boolean atRisk = unfinished && dueDate != null && dueDate.isBefore(LocalDate.now(ZoneOffset.UTC));
        return new ProjectTimelineResponse.IssueBar(issue.getId(), issue.getIssueNumber(), issue.getTitle(),
                issue.getStatus(), issue.getPriority(), startDate, dueDate, atRisk);
    }

    private static ProjectTimelineResponse.Dependency toDependency(IssueLink link) {
        return new ProjectTimelineResponse.Dependency(link.getSourceIssueId(), link.getTargetIssueId(), link.getType());
    }

    private List<IssueStatus> allowedTransitions(IssueStatus status) {
        switch (status) {
            case OPEN:
                return List.of(IssueStatus.IN_PROGRESS);
            case IN_PROGRESS:
                return List.of(IssueStatus.RESOLVED);
            case RESOLVED:
                return List.of(IssueStatus.CLOSED, IssueStatus.REOPENED);
            case REOPENED:
                return List.of(IssueStatus.IN_PROGRESS, IssueStatus.RESOLVED);
            case CLOSED:
                return List.of();
            default:
                return List.of();
        }
    }

    private void requireProjectMember(UUID projectId, UUID userId) {
        projectAccess.requireMember(projectAccess.project(projectId), userId);
    }

    private LocalDate localDate(Instant instant) {
        return instant.atZone(ZoneOffset.UTC).toLocalDate();
    }

    private static class TimelineGroupMatch {
        private final List<ProjectTimelineResponse.IssueBar> issues;

        private TimelineGroupMatch(List<ProjectTimelineResponse.IssueBar> issues) {
            this.issues = issues;
        }

        private List<ProjectTimelineResponse.IssueBar> getIssues() {
            return issues;
        }
    }
}
