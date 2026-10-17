package com.frietsync.backend.service.impl.activity;

import com.frietsync.backend.dto.activity.ActivityResponse;
import com.frietsync.backend.dto.activity.DelayedIssueResponse;
import com.frietsync.backend.dto.activity.PresenceResponse;
import com.frietsync.backend.dto.activity.ProjectSummaryResponse;
import com.frietsync.backend.dto.issue.IssuePageResponse;
import com.frietsync.backend.dto.issue.IssueSummaryResponse;
import com.frietsync.backend.entity.issue.Issue;
import com.frietsync.backend.entity.issue.IssueAssignee;
import com.frietsync.backend.entity.issue.IssueHistory;
import com.frietsync.backend.entity.issue.IssueHistoryAction;
import com.frietsync.backend.entity.issue.IssueStatus;
import com.frietsync.backend.entity.project.Project;
import com.frietsync.backend.entity.project.ProjectStatus;
import com.frietsync.backend.entity.sprint.Sprint;
import com.frietsync.backend.entity.sprint.SprintStatus;
import com.frietsync.backend.entity.user.Role;
import com.frietsync.backend.entity.user.User;
import com.frietsync.backend.repository.issue.IssueAssigneeRepository;
import com.frietsync.backend.repository.issue.IssueHistoryRepository;
import com.frietsync.backend.repository.issue.IssueRepository;
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
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
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
    private final IssueAssigneeRepository assigneeRepository;
    private final SprintRepository sprintRepository;
    private final UserRepository userRepository;
    private final SummaryPdfBuilder pdfBuilder;

    @Override
    public List<PresenceResponse> presence(UUID projectId, UUID userId) {
        Project project = projectAccess.project(projectId);
        projectAccess.requireMember(project, userId);

        List<Issue> inProgress = new ArrayList<>();
        for (Issue issue : issueRepository.findByProjectIdAndDeletedAtIsNull(projectId)) {
            if (issue.getStatus() == IssueStatus.IN_PROGRESS) {
                inProgress.add(issue);
            }
        }
        List<PresenceResponse> result = new ArrayList<>();
        if (inProgress.isEmpty()) {
            return result;
        }

        Map<UUID, Issue> issueById = new HashMap<>();
        List<UUID> issueIds = new ArrayList<>();
        for (Issue issue : inProgress) {
            issueById.put(issue.getId(), issue);
            issueIds.add(issue.getId());
        }

        Map<UUID, List<IssueSummaryResponse>> workByUser = new LinkedHashMap<>();
        for (IssueAssignee assignee : assigneeRepository.findByIssueIdIn(issueIds)) {
            Issue issue = issueById.get(assignee.getIssueId());
            if (issue == null) {
                continue;
            }
            List<IssueSummaryResponse> work = workByUser.get(assignee.getUserId());
            if (work == null) {
                work = new ArrayList<>();
                workByUser.put(assignee.getUserId(), work);
            }
            work.add(IssueSummaryResponse.builder()
                    .id(issue.getId())
                    .issueNumber(issue.getIssueNumber())
                    .title(issue.getTitle())
                    .status(issue.getStatus())
                    .priority(issue.getPriority())
                    .dueDate(issue.getDueDate())
                    .build());
        }

        Map<UUID, String> names = userNames(workByUser.keySet());
        for (UUID workerId : workByUser.keySet()) {
            result.add(PresenceResponse.builder()
                    .userId(workerId)
                    .name(names.get(workerId))
                    .workingOn(workByUser.get(workerId))
                    .build());
        }
        return result;
    }

    @Override
    public List<ActivityResponse> recentActivity(UUID projectId, UUID userId, int limit) {
        Project project = projectAccess.project(projectId);
        projectAccess.requireMember(project, userId);

        Pageable pageable = PageRequest.of(0, limit);
        Page<IssueHistory> entries = historyRepository.findAllForProject(projectId, pageable);
        return toActivities(entries.getContent());
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

    @Override
    public byte[] summaryPdf(UUID projectId, UUID userId) {
        Project project = projectAccess.project(projectId);
        projectAccess.require(project, userId, Role.PROJECT_MANAGER, Role.TEAM_LEAD,Role.ADMIN,Role.CONTRIBUTOR);
        return pdfBuilder.build(buildSummary(project));
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
                delayed.add(delayedIssue(issue, ChronoUnit.DAYS.between(due, today), "OVERDUE"));
            } else if (finishedAt != null) {
                LocalDate finishedDate = finishedAt.atZone(ZoneOffset.UTC).toLocalDate();
                if (finishedDate.isAfter(due)) {
                    completedLate++;
                    delayed.add(delayedIssue(issue, ChronoUnit.DAYS.between(due, finishedDate),
                            "COMPLETED_LATE"));
                }
            }
        }

        int planned = 0;
        int active = 0;
        int completed = 0;
        int overdueSprints = 0;
        List<Sprint> sprints = sprintRepository.findByProjectId(project.getId());
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

        boolean projectDelayed = false;
        long projectDaysLate = 0;
        if (project.getDeadline() != null && project.getDeadline().isBefore(today)
                && project.getStatus() != ProjectStatus.COMPLETED
                && project.getStatus() != ProjectStatus.CANCELLED) {
            projectDelayed = true;
            projectDaysLate = ChronoUnit.DAYS.between(project.getDeadline(), today);
        }

        return ProjectSummaryResponse.builder()
                .projectId(project.getId())
                .projectName(project.getName())
                .projectStatus(project.getStatus())
                .deadline(project.getDeadline())
                .projectDelayed(projectDelayed)
                .projectDaysLate(projectDaysLate)
                .generatedAt(Instant.now())
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

    private DelayedIssueResponse delayedIssue(Issue issue, long daysLate, String state) {
        return DelayedIssueResponse.builder()
                .issueId(issue.getId())
                .issueNumber(issue.getIssueNumber())
                .title(issue.getTitle())
                .status(issue.getStatus())
                .dueDate(issue.getDueDate())
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
                    .issueId(entry.getIssueId())
                    .issueNumber(issueNumber)
                    .issueTitle(issueTitle)
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

    private Map<UUID, String> userNames(Collection<UUID> ids) {
        Map<UUID, String> names = new HashMap<>();
        for (User user : userRepository.findAllById(ids)) {
            names.put(user.getId(), user.getName());
        }
        return names;
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