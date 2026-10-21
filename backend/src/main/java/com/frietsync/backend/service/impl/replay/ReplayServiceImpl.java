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

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
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
    private static class Facts {
        private final Map<ReplayPhaseType, Instant> dates = new HashMap<>();
        private final Map<ReplayPhaseType, Integer> counts = new HashMap<>();
        private final Map<ReplayPhaseType, String> causes = new HashMap<>();
    }

    @Override
    @Transactional
    public ReplayTimelineResponse timeline(UUID projectId, UUID userId) {
        Project project = projectAccess.project(projectId);
        projectAccess.requireMember(project, userId);

        Instant now = Instant.now();
        List<Issue> issues = issueRepository.findByProjectIdAndDeletedAtIsNull(projectId);
        List<Sprint> sprints = sprintRepository.findByProjectId(projectId);
        Facts facts = collectFacts(project, issues, sprints, now);
        List<ProjectReplayPhase> phases = loadPhases(project, facts.dates);
        Map<ReplayPhaseType, Instant> ends = phaseEnds(phases, now);

        Instant completedAt = null;
        List<ReplayPhaseResponse> items = new ArrayList<>();
        for (ProjectReplayPhase phase : phases) {
            if (phase.getPhaseType() == ReplayPhaseType.COMPLETED && phase.getPhaseDate() != null
                    && !phase.getPhaseDate().isAfter(now)) {
                completedAt = phase.getPhaseDate();
            }
            items.add(toResponse(phase, facts, ends, now));
        }

        boolean completed = project.getStatus() == ProjectStatus.COMPLETED && completedAt != null;
        Instant to = now;
        if (completed && completedAt.isAfter(project.getCreatedAt())) {
            to = completedAt;
        }

        return ReplayTimelineResponse.builder()
                .projectId(project.getId())
                .projectName(project.getName())
                .from(project.getCreatedAt())
                .to(to)
                .completed(completed)
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
        phaseRepository.save(phase);

        Instant now = Instant.now();
        List<Issue> issues = issueRepository.findByProjectIdAndDeletedAtIsNull(projectId);
        List<Sprint> sprints = sprintRepository.findByProjectId(projectId);
        Facts facts = collectFacts(project, issues, sprints, now);
        List<ProjectReplayPhase> phases = loadPhases(project, facts.dates);
        Map<ReplayPhaseType, Instant> ends = phaseEnds(phases, now);

        for (ProjectReplayPhase current : phases) {
            if (current.getId().equals(phase.getId())) {
                return toResponse(current, facts, ends, now);
            }
        }
        return toResponse(phase, facts, ends, now);
    }

    @Override
    @Transactional
    public ReplaySnapshotResponse snapshot(UUID projectId, Instant at, UUID userId) {
        Project project = projectAccess.project(projectId);
        projectAccess.requireMember(project, userId);

        Instant now = Instant.now();
        boolean isNow = at == null || !at.isBefore(now);
        Instant moment = now;
        if (!isNow) {
            moment = at;
        }
        if (moment.isBefore(project.getCreatedAt())) {
            throw new BadRequestException("Time is before the project was created");
        }
        LocalDate day = moment.atZone(ZoneOffset.UTC).toLocalDate();

        List<Issue> issues = issueRepository.findByProjectIdAndDeletedAtIsNull(projectId);
        List<Sprint> sprints = sprintRepository.findByProjectId(projectId);
        Facts facts = collectFacts(project, issues, sprints, now);
        List<ProjectReplayPhase> phases = loadPhases(project, facts.dates);

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
            IssueStatus bucket = statusAt(issue, moment, isNow);
            if (bucket == IssueStatus.CLOSED) {
                closed++;
            } else if (bucket == IssueStatus.RESOLVED) {
                resolved++;
            } else {
                open++;
                if (issue.getDueDate() != null && issue.getDueDate().isBefore(day)) {
                    overdue++;
                }
            }

            IssueStatus shown = bucket;
            if (isNow) {
                shown = issue.getStatus();
            }
            visible.add(IssueSummaryResponse.builder()
                    .id(issue.getId())
                    .issueNumber(issue.getIssueNumber())
                    .title(issue.getTitle())
                    .status(shown)
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

    private IssueStatus statusAt(Issue issue, Instant moment, boolean isNow) {
        if (isNow) {
            if (issue.getStatus() == IssueStatus.CLOSED) {
                return IssueStatus.CLOSED;
            }
            if (issue.getStatus() == IssueStatus.RESOLVED) {
                return IssueStatus.RESOLVED;
            }
            return IssueStatus.OPEN;
        }
        if (issue.getClosedAt() != null && !issue.getClosedAt().isAfter(moment)) {
            return IssueStatus.CLOSED;
        }
        if (issue.getResolvedAt() != null && !issue.getResolvedAt().isAfter(moment)) {
            return IssueStatus.RESOLVED;
        }
        return IssueStatus.OPEN;
    }

    private Facts collectFacts(Project project, List<Issue> issues, List<Sprint> sprints, Instant now) {
        Facts facts = new Facts();
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
        LocalDate completeDate = project.getCompleteDate();
        boolean projectLate = false;
        long projectDaysLate = 0;
        if (deadline != null) {
            Instant deadlineEnd = startOfDay(deadline.plusDays(1));
            if (project.getStatus() == ProjectStatus.COMPLETED) {
                projectLate = completeDate != null && completeDate.isAfter(deadline);
                if (projectLate) {
                    projectDaysLate = ChronoUnit.DAYS.between(deadline, completeDate);
                }
            } else if (project.getStatus() == ProjectStatus.CANCELLED) {
                projectLate = false;
            } else {
                projectLate = deadlineEnd.isBefore(now);
                if (projectLate) {
                    projectDaysLate = ChronoUnit.DAYS.between(deadline, LocalDate.now(ZoneOffset.UTC));
                }
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
            if (completeDate != null) {
                completedAt = startOfDay(completeDate);
            } else {
                completedAt = project.getUpdatedAt();
            }
        }

        facts.dates.put(ReplayPhaseType.CREATED, project.getCreatedAt());
        facts.dates.put(ReplayPhaseType.PLANNED, plannedAt);
        facts.dates.put(ReplayPhaseType.DELAYED, firstDelayAt);
        facts.dates.put(ReplayPhaseType.ISSUES, firstIssueAt);
        facts.dates.put(ReplayPhaseType.RESOLVED, firstFinishedAt);
        facts.dates.put(ReplayPhaseType.COMPLETED, completedAt);

        facts.counts.put(ReplayPhaseType.CREATED, 0);
        facts.counts.put(ReplayPhaseType.PLANNED, sprints.size());
        facts.counts.put(ReplayPhaseType.DELAYED, delayedCount);
        facts.counts.put(ReplayPhaseType.ISSUES, issues.size());
        facts.counts.put(ReplayPhaseType.RESOLVED, finishedCount);
        facts.counts.put(ReplayPhaseType.COMPLETED, 0);

        facts.causes.put(ReplayPhaseType.CREATED, "The project was set up");

        if (project.getStartDate() != null) {
            facts.causes.put(ReplayPhaseType.PLANNED, "Project start date was set to " + project.getStartDate()
                    + " (" + sprints.size() + " sprint(s) planned)");
        } else if (!sprints.isEmpty()) {
            facts.causes.put(ReplayPhaseType.PLANNED, "The first sprint was created (" + sprints.size()
                    + " sprint(s) planned)");
        }

        StringBuilder delayCause = new StringBuilder();
        if (delayedCount > 0) {
            delayCause.append(delayedCount).append(" issue(s) passed their due date");
        }
        if (projectLate) {
            if (delayCause.length() > 0) {
                delayCause.append("; ");
            }
            delayCause.append("the project deadline ").append(deadline).append(" was missed by ")
                    .append(projectDaysLate).append(" day(s)");
        }
        if (delayCause.length() > 0) {
            facts.causes.put(ReplayPhaseType.DELAYED, delayCause.toString());
        }

        if (!issues.isEmpty()) {
            facts.causes.put(ReplayPhaseType.ISSUES, "The first issue was raised, " + issues.size()
                    + " issue(s) in total");
        }
        if (finishedCount > 0) {
            facts.causes.put(ReplayPhaseType.RESOLVED, "The first issue was resolved, " + finishedCount + " of "
                    + issues.size() + " issue(s) resolved or closed");
        }
        if (completedAt != null) {
            String text = "The project was marked as completed";
            if (projectLate) {
                text = text + " " + projectDaysLate + " day(s) after its deadline";
            } else if (deadline != null) {
                text = text + " on or before its deadline";
            }
            facts.causes.put(ReplayPhaseType.COMPLETED, text);
        }
        return facts;
    }

    private Map<ReplayPhaseType, Instant> phaseEnds(List<ProjectReplayPhase> phases, Instant now) {
        List<ProjectReplayPhase> reached = new ArrayList<>();
        for (ProjectReplayPhase phase : phases) {
            if (phase.getPhaseDate() != null && !phase.getPhaseDate().isAfter(now)) {
                reached.add(phase);
            }
        }
        reached.sort((a, b) -> a.getPhaseDate().compareTo(b.getPhaseDate()));

        Map<ReplayPhaseType, Instant> ends = new HashMap<>();
        for (int i = 0; i < reached.size(); i++) {
            ProjectReplayPhase phase = reached.get(i);
            if (i + 1 < reached.size()) {
                ends.put(phase.getPhaseType(), reached.get(i + 1).getPhaseDate());
            } else if (phase.getPhaseType() != ReplayPhaseType.COMPLETED) {
                ends.put(phase.getPhaseType(), now);
            }
        }
        return ends;
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

    private ReplayPhaseResponse toResponse(ProjectReplayPhase phase, Facts facts,
                                           Map<ReplayPhaseType, Instant> ends, Instant now) {
        Instant start = phase.getPhaseDate();
        boolean reached = start != null && !start.isAfter(now);

        Instant end = null;
        long durationDays = 0;
        String cause = "Not reached yet";
        if (reached) {
            end = ends.get(phase.getPhaseType());
            if (end != null && !end.isBefore(start)) {
                durationDays = Duration.between(start, end).toDays();
            }
            cause = facts.causes.get(phase.getPhaseType());
            if (cause == null) {
                cause = "No details recorded";
            }
        }

        Integer count = facts.counts.get(phase.getPhaseType());
        int countValue = 0;
        if (count != null) {
            countValue = count;
        }
        return ReplayPhaseResponse.builder()
                .id(phase.getId())
                .phaseType(phase.getPhaseType())
                .title(phase.getTitle())
                .description(phase.getDescription())
                .phaseDate(start)
                .endDate(end)
                .durationDays(durationDays)
                .cause(cause)
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
