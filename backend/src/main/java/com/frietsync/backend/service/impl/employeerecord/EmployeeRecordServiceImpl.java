package com.frietsync.backend.service.impl.employeerecord;

import com.frietsync.backend.dto.employeerecord.EmployeeRecordResponse;
import com.frietsync.backend.dto.employeerecord.IssueSummary;
import com.frietsync.backend.dto.employeerecord.RecordActivityResponse;
import com.frietsync.backend.dto.employeerecord.RecordGithubRepoActivity;
import com.frietsync.backend.dto.employeerecord.RecordIssueItem;
import com.frietsync.backend.dto.employeerecord.RecordIssuesResponse;
import com.frietsync.backend.dto.employeerecord.RecordSprintItem;
import com.frietsync.backend.dto.employeerecord.RecordSprintsResponse;
import com.frietsync.backend.dto.employeerecord.SprintSummary;
import com.frietsync.backend.entity.issue.Issue;
import com.frietsync.backend.entity.issue.IssueHistoryAction;
import com.frietsync.backend.entity.issue.IssueStatus;
import com.frietsync.backend.entity.sprint.Sprint;
import com.frietsync.backend.entity.sprint.SprintStatus;
import com.frietsync.backend.entity.user.Role;
import com.frietsync.backend.entity.user.User;
import com.frietsync.backend.exception.ForbiddenException;
import com.frietsync.backend.exception.ResourceNotFoundException;
import com.frietsync.backend.repository.issue.IssueHistoryRepository;
import com.frietsync.backend.repository.issue.IssueRepository;
import com.frietsync.backend.repository.project.ProjectMemberRepository;
import com.frietsync.backend.repository.project.ProjectRepository;
import com.frietsync.backend.repository.sprint.SprintRepository;
import com.frietsync.backend.repository.user.UserRepository;
import com.frietsync.backend.service.employeerecord.EmployeeRecordService;
import com.frietsync.backend.service.employeerecord.GithubActivity;
import com.frietsync.backend.service.employeerecord.GithubActivityProvider;
import com.frietsync.backend.service.employeerecord.GithubRepoActivity;
import com.frietsync.backend.service.impl.project.ProjectAccess;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class EmployeeRecordServiceImpl implements EmployeeRecordService {

    private final UserRepository userRepository;
    private final ProjectMemberRepository memberRepository;
    private final IssueRepository issueRepository;
    private final IssueHistoryRepository historyRepository;
    private final SprintRepository sprintRepository;
    private final GithubActivityProvider githubActivityProvider;
    private final ProjectRepository projectRepository;
    private final ProjectAccess projectAccess;

    @Override
    @Transactional(readOnly = true)
    public EmployeeRecordResponse overview(UUID userId, UUID viewerId) {
        Access access = authorize(userId, viewerId);
        User user = access.target();

        int projectCount = 0;
        for (UUID projectId : memberRepository.findProjectIdsOfUser(userId)) {
            if (access.allows(projectId)) {
                projectCount++;
            }
        }

        return EmployeeRecordResponse.builder()
                .userId(user.getId())
                .name(user.getName())
                .email(user.getEmail())
                .role(user.getRole())
                .jobTitle(user.getJobTitle())
                .bio(user.getBio())
                .githubUrl(user.getGithubUrl())
                .active(user.isActive())
                .projectCount(projectCount)
                .issues(buildIssues(userId, access).getSummary())
                .sprints(buildSprints(userId, access).getSummary())
                .activity(buildActivity(userId, access))
                .build();
    }

    @Override
    @Transactional(readOnly = true)
    public RecordIssuesResponse issues(UUID userId, UUID viewerId) {
        return buildIssues(userId, authorize(userId, viewerId));
    }

    @Override
    @Transactional(readOnly = true)
    public RecordSprintsResponse sprints(UUID userId, UUID viewerId) {
        return buildSprints(userId, authorize(userId, viewerId));
    }

    @Override
    @Transactional(readOnly = true)
    public RecordActivityResponse activity(UUID userId, UUID viewerId) {
        return buildActivity(userId, authorize(userId, viewerId));
    }

    private Access authorize(UUID targetId, UUID viewerId) {
        User target = userRepository.findById(targetId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));
        User viewer = userRepository.findById(viewerId)
                .orElseThrow(() -> new ForbiddenException("You don't have permission to do this"));
        UUID workspaceId = projectAccess.requireActiveWorkspace(viewerId);
        if (!workspaceId.equals(target.getWorkspaceId())) {
            throw new ResourceNotFoundException("User not found");
        }
        Set<UUID> targetProjects = new HashSet<>();
        projectRepository.findAllForUser(targetId).stream()
                .filter(project -> workspaceId.equals(project.getWorkspaceId()))
                .forEach(project -> targetProjects.add(project.getId()));

        if (viewer.getId().equals(target.getId())) {
            return new Access(target, targetProjects);
        }
        if (viewer.getRole() == Role.ADMIN) {
            return new Access(target, targetProjects);
        }

        Set<UUID> shared = new HashSet<>(memberRepository.findManagedProjectIds(viewerId));
        shared.retainAll(targetProjects);
        if (shared.isEmpty()) {
            throw new ForbiddenException("You don't have permission to view this employee record");
        }
        return new Access(target, shared);
    }

    private RecordIssuesResponse buildIssues(UUID userId, Access access) {
        Map<UUID, Issue> issues = new LinkedHashMap<>();
        Set<UUID> createdIds = collect(issueRepository.findByCreatedByAndDeletedAtIsNull(userId), issues, access);
        Set<UUID> resolvedIds = collect(issueRepository.findResolvedBy(userId), issues, access);
        Set<UUID> assignedIds = collect(issueRepository.findAssignedTo(userId), issues, access);

        Set<UUID> reopenedIds = new HashSet<>();
        if (!issues.isEmpty()) {
            reopenedIds.addAll(historyRepository.findReopenedIssueIds(issues.keySet()));
        }

        List<RecordIssueItem> items = new ArrayList<>();
        double totalHours = 0;
        int timed = 0;
        for (Issue issue : issues.values()) {
            boolean finished = issue.getStatus() == IssueStatus.RESOLVED
                    || issue.getStatus() == IssueStatus.CLOSED;
            Instant resolvedAt = finished ? issue.getResolvedAt() : null;
            Double hours = hoursBetween(issue.getCreatedAt(), resolvedAt);
            boolean resolvedByUser = resolvedIds.contains(issue.getId());
            if (resolvedByUser && hours != null) {
                totalHours += hours;
                timed++;
            }
            items.add(RecordIssueItem.builder()
                    .issueId(issue.getId())
                    .projectId(issue.getProjectId())
                    .issueNumber(issue.getIssueNumber())
                    .title(issue.getTitle())
                    .status(issue.getStatus())
                    .priority(issue.getPriority())
                    .createdByUser(createdIds.contains(issue.getId()))
                    .resolvedByUser(resolvedByUser)
                    .assignedToUser(assignedIds.contains(issue.getId()))
                    .reopened(reopenedIds.contains(issue.getId()))
                    .createdAt(issue.getCreatedAt())
                    .resolvedAt(resolvedAt)
                    .hoursToResolve(hours)
                    .build());
        }
        items.sort(Comparator.comparing(RecordIssueItem::getCreatedAt, Comparator.reverseOrder()));

        int reopened = 0;
        for (UUID id : reopenedIds) {
            if (resolvedIds.contains(id) || assignedIds.contains(id)) {
                reopened++;
            }
        }

        Double average = null;
        if (timed > 0) {
            average = Math.round(totalHours / timed * 100.0) / 100.0;
        }

        return RecordIssuesResponse.builder()
                .userId(userId)
                .summary(IssueSummary.builder()
                        .created(createdIds.size())
                        .resolved(resolvedIds.size())
                        .reopened(reopened)
                        .averageHoursToResolve(average)
                        .build())
                .items(items)
                .build();
    }

    private RecordSprintsResponse buildSprints(UUID userId, Access access) {
        List<Sprint> sprints = new ArrayList<>();
        for (Sprint sprint : sprintRepository.findByAssignedContributorId(userId)) {
            if (access.allows(sprint.getProjectId())) {
                sprints.add(sprint);
            }
        }
        sprints.sort(Comparator.comparing(Sprint::getCreatedAt, Comparator.reverseOrder()));

        int completed = 0;
        int active = 0;
        int planned = 0;
        List<RecordSprintItem> items = new ArrayList<>();
        for (Sprint sprint : sprints) {
            if (sprint.getStatus() == SprintStatus.COMPLETED) {
                completed++;
            } else if (sprint.getStatus() == SprintStatus.ACTIVE) {
                active++;
            } else {
                planned++;
            }
            items.add(RecordSprintItem.builder()
                    .sprintId(sprint.getId())
                    .projectId(sprint.getProjectId())
                    .sprintNumber(sprint.getSprintNumber())
                    .name(sprint.getName())
                    .status(sprint.getStatus())
                    .startDate(sprint.getStartDate())
                    .endDate(sprint.getEndDate())
                    .completed(sprint.getStatus() == SprintStatus.COMPLETED)
                    .build());
        }

        int committed = sprints.size();
        Double rate = null;
        if (committed > 0) {
            rate = Math.round(completed * 1000.0 / committed) / 10.0;
        }

        return RecordSprintsResponse.builder()
                .userId(userId)
                .summary(SprintSummary.builder()
                        .committed(committed)
                        .completed(completed)
                        .active(active)
                        .planned(planned)
                        .completionRate(rate)
                        .build())
                .items(items)
                .build();
    }

    private RecordActivityResponse buildActivity(UUID userId, Access access) {
        User user = access.target();
        Collection<UUID> projectIds = access.restricted()
                ? access.projectIds()
                : memberRepository.findProjectIdsOfUser(userId);
        GithubActivity github = githubActivityProvider.fetch(user, projectIds);

        List<RecordGithubRepoActivity> repositories = new ArrayList<>();
        for (GithubRepoActivity repo : github.repositories()) {
            repositories.add(new RecordGithubRepoActivity(repo.repository(), repo.commits(),
                    repo.pullRequests(), repo.mergedPullRequests(), repo.reviews()));
        }

        return RecordActivityResponse.builder()
                .userId(userId)
                .githubUrl(user.getGithubUrl())
                .githubConnected(github.connected())
                .githubLogin(github.login())
                .commits(github.commits())
                .pullRequests(github.pullRequests())
                .mergedPullRequests(github.mergedPullRequests())
                .githubReviews(github.reviews())
                .githubFailedRepositories(github.failedRepositories())
                .githubRepositories(repositories)
                .issueReviewsApproved(countActions(userId, IssueHistoryAction.REVIEW_APPROVED, access))
                .issueReviewsRejected(countActions(userId, IssueHistoryAction.REVIEW_REJECTED, access))
                .designSubmissions(null)
                .designReviews(null)
                .build();
    }

    private long countActions(UUID userId, IssueHistoryAction action, Access access) {
        if (!access.restricted()) {
            return historyRepository.countByActorIdAndAction(userId, action);
        }
        if (access.projectIds().isEmpty()) {
            return 0;
        }
        return historyRepository.countByActorAndActionInProjects(userId, action, access.projectIds());
    }

    private Set<UUID> collect(List<Issue> found, Map<UUID, Issue> all, Access access) {
        Set<UUID> ids = new HashSet<>();
        for (Issue issue : found) {
            if (access.allows(issue.getProjectId())) {
                all.put(issue.getId(), issue);
                ids.add(issue.getId());
            }
        }
        return ids;
    }

    private Double hoursBetween(Instant from, Instant to) {
        if (from == null || to == null) {
            return null;
        }
        long seconds = Math.max(0, Duration.between(from, to).getSeconds());
        return Math.round(seconds / 36.0) / 100.0;
    }

    private record Access(User target, Set<UUID> projectIds) {

        boolean restricted() {
            return projectIds != null;
        }

        boolean allows(UUID projectId) {
            return projectIds == null || projectIds.contains(projectId);
        }
    }
}
