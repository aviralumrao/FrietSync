package com.frietsync.backend.service.impl.replay;

import com.frietsync.backend.dto.issue.IssueSummaryResponse;
import com.frietsync.backend.dto.replay.ReplayPhaseResponse;
import com.frietsync.backend.dto.replay.ReplaySnapshotResponse;
import com.frietsync.backend.dto.replay.ReplayTimelineResponse;
import com.frietsync.backend.dto.replay.UpdateReplayPhaseRequest;
import com.frietsync.backend.entity.issue.Issue;
import com.frietsync.backend.entity.issue.IssueStatus;
import com.frietsync.backend.entity.project.Project;
import com.frietsync.backend.entity.project.ProjectStatus;
import com.frietsync.backend.entity.replay.ProjectReplayPhase;
import com.frietsync.backend.entity.replay.ReplayPhaseType;
import com.frietsync.backend.entity.sprint.Sprint;
import com.frietsync.backend.entity.user.Role;
import com.frietsync.backend.exception.BadRequestException;
import com.frietsync.backend.exception.ResourceNotFoundException;
import com.frietsync.backend.repository.issue.IssueRepository;
import com.frietsync.backend.repository.replay.ProjectReplayPhaseRepository;
import com.frietsync.backend.repository.sprint.SprintRepository;
import com.frietsync.backend.service.impl.project.ProjectAccess;
import com.frietsync.backend.service.replay.ReplayService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class ReplayServiceImpl implements ReplayService {

    private final ProjectAccess projectAccess;
    private final IssueRepository issueRepository;
    private final SprintRepository sprintRepository;
    private final ProjectReplayPhaseRepository phaseRepository;

    @Override
    @Transactional
    public ReplayTimelineResponse timeline(UUID projectId, UUID userId) {
        Project project = projectAccess.project(projectId);
        projectAccess.requireMember(project, userId);

        List<Issue> issues = issueRepository.findByProjectIdAndDeletedAtIsNull(projectId);
        List<Sprint> sprints = sprintRepository.findByProjectId(projectId);
        Map<ReplayPhaseType, Instant> dates = new HashMap<>();
        Map<ReplayPhaseType, Integer> counts = new HashMap<>();
        fillFacts(project, issues, sprints, dates, counts);
        List<ProjectReplayPhase> phases = loadPhases(project, dates);

        List<ReplayPhaseResponse> items = new ArrayList<>();
        for (ProjectReplayPhase phase : phases) {
            items.add(toResponse(phase, counts));
        }

        return ReplayTimelineResponse.builder()
                .projectId(project.getId())
                .projectName(project.getName())
                .from(project.getCreatedAt())
                .to(Instant.now())
                .phases(items)
                .build();
    }

    @Override
    @Transactional
    public ReplayPhaseResponse updatePhase(UUID projectId, UUID phaseId, UpdateReplayPhaseRequest request,
                                           UUID userId) {
        Project project = projectAccess.project(projectId);
        projectAccess.require(project, userId, Role.PROJECT_MANAGER);

        ProjectReplayPhase phase = phaseRepository.findByIdAndProjectId(phaseId, projectId)
                .orElseThrow(() -> new ResourceNotFoundException("Phase not found"));

        if (request.getTitle() == null && request.getDescription() == null && request.getPhaseDate() == null) {
            throw new BadRequestException("Nothing to update");
        }
        if (request.getTitle() != null) {
            if (request.getTitle().isBlank()) {
                throw new BadRequestException("Title must not be blank");
            }
            phase.setTitle(request.getTitle().trim());
        }
        if (request.getDescription() != null) {
            phase.setDescription(request.getDescription());
        }
        if (request.getPhaseDate() != null) {
            phase.setPhaseDate(request.getPhaseDate());
            phase.setDateOverridden(true);
        }
        phase = phaseRepository.save(phase);

        List<Issue> issues = issueRepository.findByProjectIdAndDeletedAtIsNull(projectId);
        List<Sprint> sprints = sprintRepository.findByProjectId(projectId);
        Map<ReplayPhaseType, Instant> dates = new HashMap<>();
        Map<ReplayPhaseType, Integer> counts = new HashMap<>();
        fillFacts(project, issues, sprints, dates, counts);
        return toResponse(phase, counts);
    }

    @Override
    @Transactional
    public ReplaySnapshotResponse snapshot(UUID projectId, Instant at, UUID userId) {
        Project project = projectAccess.project(projectId);
        projectAccess.requireMember(project, userId);

        Instant now = Instant.now();
        Instant moment = now;
        if (at != null && at.isBefore(now)) {
            moment = at;
        }
        if (moment.isBefore(project.getCreatedAt())) {
            throw new BadRequestException("Time is before the project was created");
        }
        LocalDate day = moment.atZone(ZoneOffset.UTC).toLocalDate();

        List<Issue> issues = issueRepository.findByProjectIdAndDeletedAtIsNull(projectId);
        List<Sprint> sprints = sprintRepository.findByProjectId(projectId);
        Map<ReplayPhaseType, Instant> dates = new HashMap<>();
        Map<ReplayPhaseType, Integer> counts = new HashMap<>();
        fillFacts(project, issues, sprints, dates, counts);
        List<ProjectReplayPhase> phases = loadPhases(project, dates);

        ReplayPhaseType currentPhase = null;
        Instant currentDate = null;
        for (ProjectReplayPhase phase : phases) {
            Instant date = phase.getPhaseDate();
            if (date != null && !date.isAfter(moment) && (currentDate == null || !date.isBefore(currentDate))) {
                currentPhase = phase.getPhaseType();
                currentDate = date;
            }
        }

        int open = 0;
        int resolved = 0;
        int closed = 0;
        int overdue = 0;
        List<IssueSummaryResponse> visible = new ArrayList<>();
        for (Issue issue : issues) {
            if (issue.getCreatedAt().isAfter(moment)) {
                continue;
            }
            IssueStatus status = IssueStatus.OPEN;
            if (issue.getClosedAt() != null && !issue.getClosedAt().isAfter(moment)) {
                status = IssueStatus.CLOSED;
                closed++;
            } else if (issue.getResolvedAt() != null && !issue.getResolvedAt().isAfter(moment)) {
                status = IssueStatus.RESOLVED;
                resolved++;
            } else {
                open++;
                if (issue.getDueDate() != null && issue.getDueDate().isBefore(day)) {
                    overdue++;
                }
            }
            visible.add(IssueSummaryResponse.builder()
                    .id(issue.getId())
                    .issueNumber(issue.getIssueNumber())
                    .title(issue.getTitle())
                    .status(status)
                    .priority(issue.getPriority())
                    .dueDate(issue.getDueDate())
                    .build());
        }

        int sprintCount = 0;
        for (Sprint sprint : sprints) {
            if (!sprint.getCreatedAt().isAfter(moment)) {
                sprintCount++;
            }
        }

        double progress = 0;
        if (!visible.isEmpty()) {
            progress = round((resolved + closed) * 100.0 / visible.size());
        }

        return ReplaySnapshotResponse.builder()
                .projectId(project.getId())
                .at(moment)
                .currentPhase(currentPhase)
                .totalIssues(visible.size())
                .openIssues(open)
                .resolvedIssues(resolved)
                .closedIssues(closed)
                .overdueIssues(overdue)
                .progressPercentage(progress)
                .totalSprints(sprintCount)
                .issues(visible)
                .build();
    }

    private void fillFacts(Project project, List<Issue> issues, List<Sprint> sprints,
                           Map<ReplayPhaseType, Instant> dates, Map<ReplayPhaseType, Integer> counts) {
        Instant now = Instant.now();
        Instant firstIssueAt = null;
        Instant firstFinishedAt = null;
        Instant firstDelayAt = null;
        int finishedCount = 0;
        int delayedCount = 0;

        for (Issue issue : issues) {
            if (firstIssueAt == null || issue.getCreatedAt().isBefore(firstIssueAt)) {
                firstIssueAt = issue.getCreatedAt();
            }

            Instant finishedAt = finishedAt(issue);
            if (finishedAt != null) {
                finishedCount++;
                if (firstFinishedAt == null || finishedAt.isBefore(firstFinishedAt)) {
                    firstFinishedAt = finishedAt;
                }
            }

            if (issue.getDueDate() != null) {
                Instant delayStart = startOfDay(issue.getDueDate().plusDays(1));
                boolean late;
                if (finishedAt == null) {
                    late = delayStart.isBefore(now);
                } else {
                    late = !finishedAt.isBefore(delayStart);
                }
                if (late) {
                    delayedCount++;
                    if (firstDelayAt == null || delayStart.isBefore(firstDelayAt)) {
                        firstDelayAt = delayStart;
                    }
                }
            }
        }

        LocalDate deadline = project.getDeadline();
        if (deadline != null) {
            Instant deadlineEnd = startOfDay(deadline.plusDays(1));
            boolean projectLate;
            if (project.getStatus() == ProjectStatus.COMPLETED) {
                projectLate = project.getCompleteDate() != null && project.getCompleteDate().isAfter(deadline);
            } else if (project.getStatus() == ProjectStatus.CANCELLED) {
                projectLate = false;
            } else {
                projectLate = deadlineEnd.isBefore(now);
            }
            if (projectLate && (firstDelayAt == null || deadlineEnd.isBefore(firstDelayAt))) {
                firstDelayAt = deadlineEnd;
            }
        }

        Instant firstSprintAt = null;
        for (Sprint sprint : sprints) {
            if (firstSprintAt == null || sprint.getCreatedAt().isBefore(firstSprintAt)) {
                firstSprintAt = sprint.getCreatedAt();
            }
        }

        Instant plannedAt = firstSprintAt;
        if (project.getStartDate() != null) {
            plannedAt = startOfDay(project.getStartDate());
        }

        Instant completedAt = null;
        if (project.getStatus() == ProjectStatus.COMPLETED) {
            if (project.getCompleteDate() != null) {
                completedAt = startOfDay(project.getCompleteDate());
            } else {
                completedAt = project.getUpdatedAt();
            }
        }

        dates.put(ReplayPhaseType.CREATED, project.getCreatedAt());
        dates.put(ReplayPhaseType.PLANNED, plannedAt);
        dates.put(ReplayPhaseType.DELAYED, firstDelayAt);
        dates.put(ReplayPhaseType.ISSUES, firstIssueAt);
        dates.put(ReplayPhaseType.RESOLVED, firstFinishedAt);
        dates.put(ReplayPhaseType.COMPLETED, completedAt);

        counts.put(ReplayPhaseType.CREATED, 0);
        counts.put(ReplayPhaseType.PLANNED, sprints.size());
        counts.put(ReplayPhaseType.DELAYED, delayedCount);
        counts.put(ReplayPhaseType.ISSUES, issues.size());
        counts.put(ReplayPhaseType.RESOLVED, finishedCount);
        counts.put(ReplayPhaseType.COMPLETED, 0);
    }

    private List<ProjectReplayPhase> loadPhases(Project project, Map<ReplayPhaseType, Instant> dates) {
        Map<ReplayPhaseType, ProjectReplayPhase> existing = new HashMap<>();
        for (ProjectReplayPhase phase : phaseRepository.findByProjectId(project.getId())) {
            existing.put(phase.getPhaseType(), phase);
        }

        List<ProjectReplayPhase> result = new ArrayList<>();
        for (ReplayPhaseType type : ReplayPhaseType.values()) {
            Instant calculated = dates.get(type);
            ProjectReplayPhase phase = existing.get(type);
            if (phase == null) {
                phase = new ProjectReplayPhase();
                phase.setProjectId(project.getId());
                phase.setPhaseType(type);
                phase.setTitle(defaultTitle(type));
                phase.setDescription(defaultDescription(type));
                phase.setPhaseDate(calculated);
                phase = phaseRepository.save(phase);
            } else if (!phase.isDateOverridden() && !sameDate(phase.getPhaseDate(), calculated)) {
                phase.setPhaseDate(calculated);
                phase = phaseRepository.save(phase);
            }
            result.add(phase);
        }
        return result;
    }

    private ReplayPhaseResponse toResponse(ProjectReplayPhase phase, Map<ReplayPhaseType, Integer> counts) {
        boolean reached = phase.getPhaseDate() != null && !phase.getPhaseDate().isAfter(Instant.now());
        Integer count = counts.get(phase.getPhaseType());
        int countValue = 0;
        if (count != null) {
            countValue = count;
        }
        return ReplayPhaseResponse.builder()
                .id(phase.getId())
                .phaseType(phase.getPhaseType())
                .title(phase.getTitle())
                .description(phase.getDescription())
                .phaseDate(phase.getPhaseDate())
                .reached(reached)
                .dateOverridden(phase.isDateOverridden())
                .count(countValue)
                .build();
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

    private Instant startOfDay(LocalDate date) {
        return date.atStartOfDay(ZoneOffset.UTC).toInstant();
    }

    private boolean sameDate(Instant first, Instant second) {
        if (first == null || second == null) {
            return first == null && second == null;
        }
        return first.equals(second);
    }

    private double round(double value) {
        return Math.round(value * 10.0) / 10.0;
    }

    private String defaultTitle(ReplayPhaseType type) {
        switch (type) {
            case CREATED:
                return "Project created";
            case PLANNED:
                return "Planning";
            case DELAYED:
                return "Delays";
            case ISSUES:
                return "Issues raised";
            case RESOLVED:
                return "Issues resolved";
            default:
                return "Project completed";
        }
    }

    private String defaultDescription(ReplayPhaseType type) {
        switch (type) {
            case CREATED:
                return "The project was created";
            case PLANNED:
                return "Work was planned and the project start date was set";
            case DELAYED:
                return "The first issue or the project deadline was missed";
            case ISSUES:
                return "The first issue was raised";
            case RESOLVED:
                return "The first issue was resolved";
            default:
                return "The project was completed";
        }
    }
}