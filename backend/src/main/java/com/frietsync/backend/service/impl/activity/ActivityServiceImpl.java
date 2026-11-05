package com.frietsync.backend.service.impl.activity;

import com.frietsync.backend.dto.activity.ActivityResponse;
import com.frietsync.backend.dto.activity.DelayedIssueResponse;
import com.frietsync.backend.dto.activity.PresenceResponse;
import com.frietsync.backend.dto.activity.PresenceSprintResponse;
import com.frietsync.backend.dto.activity.ProjectSummaryResponse;
import com.frietsync.backend.dto.issue.IssuePageResponse;
import com.frietsync.backend.dto.issue.IssueSummaryResponse;
import com.frietsync.backend.entity.activity.ProjectActivity;
import com.frietsync.backend.entity.issue.Issue;
import com.frietsync.backend.entity.issue.IssueAssignee;
import com.frietsync.backend.entity.issue.IssueHistory;
import com.frietsync.backend.entity.issue.IssueHistoryAction;
import com.frietsync.backend.entity.issue.IssueStatus;
import com.frietsync.backend.entity.project.Project;
import com.frietsync.backend.entity.project.ProjectStatus;
import com.frietsync.backend.entity.sprint.Sprint;
import com.frietsync.backend.entity.sprint.SprintStatus;
import com.frietsync.backend.entity.user.User;
import com.frietsync.backend.repository.issue.IssueAssigneeRepository;
import com.frietsync.backend.repository.issue.IssueHistoryRepository;
import com.frietsync.backend.repository.issue.IssueRepository;
import com.frietsync.backend.repository.activity.ProjectActivityRepository;
import com.frietsync.backend.repository.sprint.SprintRepository;
import com.frietsync.backend.repository.user.UserRepository;
import com.frietsync.backend.service.activity.ActivityService;
import com.frietsync.backend.service.impl.project.ProjectAccess;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ActivityServiceImpl implements ActivityService {

    private final ProjectAccess projectAccess;
    private final IssueRepository issueRepository;
    private final IssueHistoryRepository historyRepository;
    private final ProjectActivityRepository projectActivityRepository;
    private final IssueAssigneeRepository assigneeRepository;
    private final SprintRepository sprintRepository;
    private final UserRepository userRepository;

    @Override
    public List<PresenceResponse> presence(UUID projectId, UUID userId) {
        Project project = projectAccess.project(projectId);
        projectAccess.requireMember(project, userId);

        Map<UUID, Issue> unfinished = new LinkedHashMap<>();
        for (Issue issue : issueRepository.findByProjectIdAndDeletedAtIsNull(projectId)) {
            if (isUnfinished(issue.getStatus())) {
                unfinished.put(issue.getId(), issue);
            }
        }

        Map<UUID, List<IssueSummaryResponse>> workByUser = new LinkedHashMap<>();
        Map<UUID, Integer> remainingByUser = new HashMap<>();
        if (!unfinished.isEmpty()) {
            List<UUID> issueIds = new ArrayList<>(unfinished.keySet());
            for (IssueAssignee assignee : assigneeRepository.findByIssueIdIn(issueIds)) {
                Issue issue = unfinished.get(assignee.getIssueId());
                if (issue == null) {
                    continue;
                }
                UUID workerId = assignee.getUserId();

                Integer remaining = remainingByUser.get(workerId);
                if (remaining == null) {
                    remaining = 0;
                }
                remainingByUser.put(workerId, remaining + 1);

                if (issue.getStatus() == IssueStatus.IN_PROGRESS) {
                    List<IssueSummaryResponse> work = workByUser.get(workerId);
                    if (work == null) {
                        work = new ArrayList<>();
                        workByUser.put(workerId, work);
                    }
                    work.add(IssueSummaryResponse.builder()
                            .id(issue.getId())
                            .issueNumber(issue.getIssueNumber())
                            .title(issue.getTitle())
                            .status(issue.getStatus())
                            .priority(issue.getPriority())
                            .startDate(issue.getStartDate())
                            .dueDate(issue.getDueDate())
                            .build());
                }
            }
        }

        Map<UUID, List<PresenceSprintResponse>> sprintsByUser = new LinkedHashMap<>();
        for (Sprint sprint : sprintRepository.findByProjectIdAndDeletedAtIsNull(projectId)) {
            UUID contributorId = sprint.getAssignedContributorId();
            if (sprint.getStatus() != SprintStatus.ACTIVE || contributorId == null) {
                continue;
            }
            List<PresenceSprintResponse> list = sprintsByUser.get(contributorId);
            if (list == null) {
                list = new ArrayList<>();
                sprintsByUser.put(contributorId, list);
            }
            list.add(PresenceSprintResponse.builder()
                    .id(sprint.getId())
                    .sprintNumber(sprint.getSprintNumber())
                    .name(sprint.getName())
                    .endDate(sprint.getEndDate())
                    .build());
        }

        Set<UUID> present = new LinkedHashSet<>(workByUser.keySet());
        present.addAll(sprintsByUser.keySet());

        List<PresenceResponse> result = new ArrayList<>();
        if (present.isEmpty()) {
            return result;
        }

        Map<UUID, String> names = userNames(present);
        for (UUID workerId : present) {
            List<IssueSummaryResponse> working = workByUser.get(workerId);
            if (working == null) {
                working = new ArrayList<>();
            }
            List<PresenceSprintResponse> activeSprints = sprintsByUser.get(workerId);
            if (activeSprints == null) {
                activeSprints = new ArrayList<>();
            }
            int remaining = 0;
            Integer remainingValue = remainingByUser.get(workerId);
            if (remainingValue != null) {
                remaining = remainingValue;
            }
            result.add(PresenceResponse.builder()
                    .userId(workerId)
                    .name(names.get(workerId))
                    .workingOn(working)
                    .activeSprints(activeSprints)
                    .remainingTasks(remaining)
                    .build());
        }
        return result;
    }


    @Override
    public List<ActivityResponse> recentActivity(UUID projectId, UUID userId) {
        Project project = projectAccess.project(projectId);
        projectAccess.requireMember(project, userId);

        return latestActivityForProject(projectId);
    }

    @Override
    public List<ActivityResponse> latestActivityForProject(UUID projectId) {
        Pageable pageable = PageRequest.of(0, 10);
        Page<IssueHistory> issueEntries = historyRepository.findRecentActivityForProject(projectId, pageable);
        List<ActivityResponse> activities = new ArrayList<>(toActivities(issueEntries.getContent()));
        List<ProjectActivity> projectEntries = projectActivityRepository
                .findByProjectIdAndResourceTypeInOrderByCreatedAtDescIdDesc(
                        projectId, List.of("MILESTONE", "POLL"), pageable);
        Map<UUID, String> names = userNames(projectEntries.stream().map(ProjectActivity::getActorId).toList());
        for (ProjectActivity entry : projectEntries) {
            activities.add(ActivityResponse.builder()
                    .id(entry.getId())
                    .resourceType(entry.getResourceType())
                    .resourceId(entry.getResourceId())
                    .resourceName(entry.getResourceName())
                    .message(ActivityMessageFormatter.forProjectActivity(entry, names.get(entry.getActorId())))
                    .actorId(entry.getActorId())
                    .actorName(names.get(entry.getActorId()))
                    .action(entry.getAction())
                    .createdAt(entry.getCreatedAt())
                    .build());
        }
        return activities.stream()
                .sorted(Comparator.comparing(ActivityResponse::getCreatedAt).reversed()
                        .thenComparing(ActivityResponse::getId, Comparator.reverseOrder()))
                .limit(10)
                .toList();
    }

    @Override
    public IssuePageResponse<ActivityResponse> events(UUID projectId, UUID userId, IssueHistoryAction action,
                                                      int page, int size) {
        Project project = projectAccess.project(projectId);
        projectAccess.requireMember(project, userId);

        Pageable pageable = PageRequest.of(page, size);
        Page<IssueHistory> entries;
        if (action == null) {
            entries = historyRepository.findAllForProject(projectId, pageable);
        } else {
            entries = historyRepository.findAllForProjectByAction(projectId, action, pageable);
        }
        return IssuePageResponse.<ActivityResponse>builder()
                .items(toActivities(entries.getContent()))
                .page(entries.getNumber())
                .size(entries.getSize())
                .totalElements(entries.getTotalElements())
                .totalPages(entries.getTotalPages())
                .build();
    }

    @Override
    public ProjectSummaryResponse summary(UUID projectId, UUID userId) {
        Project project = projectAccess.project(projectId);
        projectAccess.requireMember(project, userId);
        return buildSummary(project);
    }

    private ProjectSummaryResponse buildSummary(Project project) {
        LocalDate today = LocalDate.now(ZoneOffset.UTC);
        List<Issue> issues = issueRepository.findByProjectIdAndDeletedAtIsNull(project.getId());

        int open = 0;
        int inProgress = 0;
        int resolved = 0;
        int reopened = 0;
        int closed = 0;
        int overdue = 0;
        int completedLate = 0;
        double totalEstimated = 0;
        double finishedEstimated = 0;
        double finishedActual = 0;
        List<DelayedIssueResponse> delayed = new ArrayList<>();

        for (Issue issue : issues) {
            IssueStatus status = issue.getStatus();
            if (status == IssueStatus.OPEN) {
                open++;
            } else if (status == IssueStatus.IN_PROGRESS) {
                inProgress++;
            } else if (status == IssueStatus.RESOLVED) {
                resolved++;
            } else if (status == IssueStatus.REOPENED) {
                reopened++;
            } else if (status == IssueStatus.CLOSED) {
                closed++;
            }

            boolean finished = status == IssueStatus.RESOLVED || status == IssueStatus.CLOSED;
            Instant finishedAt = null;
            if (finished) {
                finishedAt = issue.getResolvedAt();
                if (finishedAt == null) {
                    finishedAt = issue.getClosedAt();
                }
            }

            Double estimate = issue.getEstimatedHours();
            if (estimate != null) {
                totalEstimated += estimate;
                if (finishedAt != null) {
                    finishedEstimated += estimate;
                    finishedActual += hoursBetween(issue.getCreatedAt(), finishedAt);
                }
            }

            LocalDate due = issue.getDueDate();
            if (due == null) {
                continue;
            }
            if (!finished && due.isBefore(today)) {
                overdue++;
                delayed.add(delayedIssue(issue, ChronoUnit.DAYS.between(due, today), "OVERDUE", null));
            } else if (finishedAt != null) {
                LocalDate finishedDate = finishedAt.atZone(ZoneOffset.UTC).toLocalDate();
                if (finishedDate.isAfter(due)) {
                    completedLate++;
                    delayed.add(delayedIssue(issue, ChronoUnit.DAYS.between(due, finishedDate),
                            "COMPLETED_LATE", finishedDate));
                }
            }
        }
        delayed.sort((a, b) -> Long.compare(b.getDaysLate(), a.getDaysLate()));

        int planned = 0;
        int active = 0;
        int completed = 0;
        int overdueSprints = 0;
        List<Sprint> sprints = sprintRepository.findByProjectIdAndDeletedAtIsNull(project.getId());
        for (Sprint sprint : sprints) {
            if (sprint.getStatus() == SprintStatus.PLANNED) {
                planned++;
            } else if (sprint.getStatus() == SprintStatus.ACTIVE) {
                active++;
            } else if (sprint.getStatus() == SprintStatus.COMPLETED) {
                completed++;
            }
            if (sprint.getStatus() != SprintStatus.COMPLETED && sprint.getEndDate() != null
                    && sprint.getEndDate().isBefore(today)) {
                overdueSprints++;
            }
        }

        ProjectStatus projectStatus = project.getStatus();
        LocalDate startDate = project.getStartDate();
        LocalDate deadline = project.getDeadline();
        LocalDate completeDate = project.getCompleteDate();

        boolean projectDelayed = false;
        long projectDaysLate = 0;
        if (deadline != null) {
            if (projectStatus == ProjectStatus.COMPLETED) {
                if (completeDate != null && completeDate.isAfter(deadline)) {
                    projectDelayed = true;
                    projectDaysLate = ChronoUnit.DAYS.between(deadline, completeDate);
                }
            } else if (projectStatus != ProjectStatus.CANCELLED && deadline.isBefore(today)) {
                projectDelayed = true;
                projectDaysLate = ChronoUnit.DAYS.between(deadline, today);
            }
        }

        Long plannedDuration = null;
        Long actualDuration = null;
        if (startDate != null && deadline != null) {
            plannedDuration = ChronoUnit.DAYS.between(startDate, deadline);
        }
        if (startDate != null) {
            if (projectStatus == ProjectStatus.COMPLETED && completeDate != null) {
                actualDuration = ChronoUnit.DAYS.between(startDate, completeDate);
            } else if (projectStatus != ProjectStatus.COMPLETED && projectStatus != ProjectStatus.CANCELLED) {
                actualDuration = ChronoUnit.DAYS.between(startDate, today);
            }
        }

        String summaryText = summaryText(project, issues.size(), resolved + closed, overdue, completedLate,
                projectDelayed, projectDaysLate);

        return ProjectSummaryResponse.builder()
                .projectId(project.getId())
                .projectName(project.getName())
                .projectStatus(projectStatus)
                .generatedAt(Instant.now())
                .summaryText(summaryText)
                .startDate(startDate)
                .deadline(deadline)
                .completedDate(completeDate)
                .plannedDurationDays(plannedDuration)
                .actualDurationDays(actualDuration)
                .projectDelayed(projectDelayed)
                .projectDaysLate(projectDaysLate)
                .totalIssues(issues.size())
                .openIssues(open)
                .inProgressIssues(inProgress)
                .resolvedIssues(resolved)
                .reopenedIssues(reopened)
                .closedIssues(closed)
                .overdueIssues(overdue)
                .completedLateIssues(completedLate)
                .delayedIssues(delayed)
                .totalEstimatedHours(round(totalEstimated))
                .finishedEstimatedHours(round(finishedEstimated))
                .finishedActualHours(round(finishedActual))
                .totalSprints(sprints.size())
                .plannedSprints(planned)
                .activeSprints(active)
                .completedSprints(completed)
                .overdueSprints(overdueSprints)
                .build();
    }

    private String summaryText(Project project, int total, int finished, int overdue, int completedLate,
                               boolean projectDelayed, long projectDaysLate) {
        StringBuilder text = new StringBuilder();
        text.append(project.getName()).append(" is ")
                .append(String.valueOf(project.getStatus()).toLowerCase().replace('_', ' ')).append(". ");
        text.append(finished).append(" of ").append(total).append(" issues are resolved or closed. ");
        if (overdue > 0 || completedLate > 0) {
            text.append(overdue).append(" issue(s) are overdue and ")
                    .append(completedLate).append(" were completed late. ");
        } else {
            text.append("No issues are delayed. ");
        }
        if (projectDelayed) {
            String verb = "is";
            if (project.getStatus() == ProjectStatus.COMPLETED) {
                verb = "was";
            }
            text.append("The project ").append(verb).append(" delayed by ")
                    .append(projectDaysLate).append(" day(s) against its deadline.");
        } else if (project.getDeadline() != null && project.getStatus() != ProjectStatus.CANCELLED) {
            text.append("The project is on schedule against its deadline.");
        }
        return text.toString().trim();
    }

    private DelayedIssueResponse delayedIssue(Issue issue, long daysLate, String state, LocalDate completedDate) {
        return DelayedIssueResponse.builder()
                .issueId(issue.getId())
                .issueNumber(issue.getIssueNumber())
                .title(issue.getTitle())
                .status(issue.getStatus())
                .dueDate(issue.getDueDate())
                .completedDate(completedDate)
                .daysLate(daysLate)
                .state(state)
                .build();
    }

    private List<ActivityResponse> toActivities(List<IssueHistory> entries) {
        List<ActivityResponse> result = new ArrayList<>();
        if (entries.isEmpty()) {
            return result;
        }

        Set<UUID> issueIds = new HashSet<>();
        Set<UUID> actorIds = new HashSet<>();
        for (IssueHistory entry : entries) {
            issueIds.add(entry.getIssueId());
            actorIds.add(entry.getActorId());
        }

        Map<UUID, Issue> issues = new HashMap<>();
        for (Issue issue : issueRepository.findAllById(issueIds)) {
            issues.put(issue.getId(), issue);
        }
        Map<UUID, String> names = userNames(actorIds);

        for (IssueHistory entry : entries) {
            Issue issue = issues.get(entry.getIssueId());
            int issueNumber = 0;
            String issueTitle = null;
            if (issue != null) {
                issueNumber = issue.getIssueNumber();
                issueTitle = issue.getTitle();
            }
            result.add(ActivityResponse.builder()
                    .id(entry.getId())
                    .resourceType("ISSUE")
                    .resourceId(entry.getIssueId())
                    .resourceName(issueTitle)
                    .message(ActivityMessageFormatter.forIssue(
                            entry, issueNumber, issueTitle, names.get(entry.getActorId())))
                    .issueId(entry.getIssueId())
                    .issueNumber(issueNumber)
                    .issueTitle(issueTitle)
                    .actorId(entry.getActorId())
                    .actorName(names.get(entry.getActorId()))
                    .action(entry.getAction().name())
                    .fieldName(entry.getFieldName())
                    .oldValue(entry.getOldValue())
                    .newValue(entry.getNewValue())
                    .comment(entry.getComment())
                    .createdAt(entry.getCreatedAt())
                    .build());
        }
        return result;
    }

    private Map<UUID, String> userNames(Collection<UUID> ids) {
        Map<UUID, String> names = new HashMap<>();
        for (User user : userRepository.findAllById(ids)) {
            names.put(user.getId(), user.getName());
        }
        return names;
    }

    private boolean isUnfinished(IssueStatus status) {
        return status != IssueStatus.RESOLVED && status != IssueStatus.CLOSED;
    }

    private double hoursBetween(Instant from, Instant to) {
        if (from == null || to == null || to.isBefore(from)) {
            return 0;
        }
        return Duration.between(from, to).toMinutes() / 60.0;
    }

    private double round(double value) {
        return Math.round(value * 10.0) / 10.0;
    }
}
