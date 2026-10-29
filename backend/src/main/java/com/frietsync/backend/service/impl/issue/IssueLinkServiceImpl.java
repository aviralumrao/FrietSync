package com.frietsync.backend.service.impl.issue;

import com.frietsync.backend.dto.issue.DependencyGraphResponse;
import com.frietsync.backend.dto.issue.IssueLinkRequest;
import com.frietsync.backend.dto.issue.IssueLinkResponse;
import com.frietsync.backend.dto.issue.IssueSummaryResponse;
import com.frietsync.backend.dto.issue.LinkedIssueResponse;
import com.frietsync.backend.entity.issue.Issue;
import com.frietsync.backend.entity.issue.IssueHistoryAction;
import com.frietsync.backend.entity.issue.IssueLink;
import com.frietsync.backend.entity.project.Project;
import com.frietsync.backend.entity.user.Role;
import com.frietsync.backend.entity.user.User;
import com.frietsync.backend.exception.BadRequestException;
import com.frietsync.backend.exception.ResourceNotFoundException;
import com.frietsync.backend.repository.issue.IssueLinkRepository;
import com.frietsync.backend.repository.issue.IssueRepository;
import com.frietsync.backend.repository.user.UserRepository;
import com.frietsync.backend.service.impl.project.ProjectAccess;
import com.frietsync.backend.service.issue.IssueLinkService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@Transactional
@RequiredArgsConstructor
public class IssueLinkServiceImpl implements IssueLinkService {

    private final IssueRepository issueRepository;
    private final UserRepository userRepository;
    private final IssueLinkRepository linkRepository;
    private final ProjectAccess access;
    private final IssueHistoryRecorder history;

    @Override
    public IssueLinkResponse create(UUID issueId, IssueLinkRequest request, UUID userId) {
        if (issueId.equals(request.getTargetIssueId())) {
            throw new BadRequestException("An issue cannot be linked to itself");
        }
        Issue source = access.issue(issueId);
        Project project = access.project(source.getProjectId());
        access.require(project, userId, Role.PROJECT_MANAGER, Role.TEAM_LEAD, Role.CONTRIBUTOR);
        Issue target = access.issue(request.getTargetIssueId());
        if (!target.getProjectId().equals(source.getProjectId())) {
            throw new BadRequestException("Both issues must belong to the same project");
        }
        if (linkRepository.existsBySourceIssueIdAndTargetIssueIdOrSourceIssueIdAndTargetIssueId(
                issueId, target.getId(), target.getId(), issueId)) {
            throw new BadRequestException("These issues are already linked");
        }

        IssueLink link = new IssueLink();
        link.setProjectId(source.getProjectId());
        link.setSourceIssueId(issueId);
        link.setTargetIssueId(target.getId());
        link.setCreatedBy(userId);
        linkRepository.save(link);
        history.record(issueId, userId, IssueHistoryAction.LINK_ADDED,
                "link", null, "#" + target.getIssueNumber());
        return toLinkResponses(List.of(link)).get(0);
    }

    @Override
    @Transactional(readOnly = true)
    public List<LinkedIssueResponse> list(UUID issueId, UUID userId) {
        Issue issue = access.issue(issueId);
        access.requireMember(access.project(issue.getProjectId()), userId);
        List<IssueLink> links = linkRepository
                .findBySourceIssueIdOrTargetIssueIdOrderByCreatedAtDesc(issueId, issueId);

        List<UUID> otherIssueIds = links.stream()
                .map(link -> link.getSourceIssueId().equals(issueId)
                        ? link.getTargetIssueId()
                        : link.getSourceIssueId())
                .distinct()
                .toList();

        Map<UUID, Issue> issueMap = issueRepository.findAllById(otherIssueIds).stream()
                .collect(Collectors.toMap(Issue::getId, i -> i));

        return links.stream()
                .map(link -> {
                    UUID otherId = link.getSourceIssueId().equals(issueId)
                            ? link.getTargetIssueId()
                            : link.getSourceIssueId();
                    Issue other = issueMap.get(otherId);
                    if (other == null) return null;
                    return LinkedIssueResponse.builder()
                            .linkId(link.getId())
                            .issueId(other.getId())
                            .issueNumber(other.getIssueNumber())
                            .issueTitle(other.getTitle())
                            .build();
                })
                .filter(java.util.Objects::nonNull)
                .toList();
    }

    @Override
    public void delete(UUID linkId, UUID userId) {
        IssueLink link = linkRepository.findById(linkId)
                .orElseThrow(() -> new ResourceNotFoundException("Link not found"));
        Issue source = access.issue(link.getSourceIssueId());
        Project project = access.project(source.getProjectId());
        access.require(project, userId, Role.PROJECT_MANAGER, Role.TEAM_LEAD, Role.CONTRIBUTOR);
        Issue target = issueRepository.findById(link.getTargetIssueId()).orElse(null);
        String oldValue = target == null ? "linked issue" : "#" + target.getIssueNumber();
        linkRepository.delete(link);
        history.record(source.getId(), userId, IssueHistoryAction.LINK_REMOVED, "link", oldValue, null);
    }

    @Override
    @Transactional(readOnly = true)
    public DependencyGraphResponse dependencyGraph(UUID projectId, UUID userId) {
        Project project = access.project(projectId);
        access.requireMember(project, userId);
        List<IssueSummaryResponse> nodes = new ArrayList<>();
        for (Issue issue : issueRepository.findByProjectIdOrderByCreatedAtDesc(projectId)) {
            if (issue.getDeletedAt() == null) nodes.add(toSummary(issue));
        }
        return DependencyGraphResponse.builder()
                .nodes(nodes)
                .edges(toLinkResponses(linkRepository.findByProjectId(projectId)))
                .build();
    }

    private List<IssueLinkResponse> toLinkResponses(List<IssueLink> links) {
        var issueIds = new LinkedHashSet<UUID>();
        for (IssueLink link : links) {
            issueIds.add(link.getSourceIssueId());
            issueIds.add(link.getTargetIssueId());
        }
        Map<UUID, Issue> issues = issueRepository.findAllById(issueIds).stream()
                .collect(Collectors.toMap(Issue::getId, issue -> issue));
        Map<UUID, String> names = userRepository.findAllById(links.stream()
                        .map(IssueLink::getCreatedBy).filter(java.util.Objects::nonNull).distinct().toList())
                .stream().collect(Collectors.toMap(User::getId, User::getName));
        List<IssueLinkResponse> result = new ArrayList<>();
        for (IssueLink link : links) {
            Issue source = issues.get(link.getSourceIssueId());
            Issue target = issues.get(link.getTargetIssueId());
            result.add(IssueLinkResponse.builder()
                    .id(link.getId()).projectId(link.getProjectId())
                    .sourceIssueId(link.getSourceIssueId())
                    .sourceIssueNumber(source == null ? 0 : source.getIssueNumber())
                    .sourceTitle(source == null ? null : source.getTitle())
                    .targetIssueId(link.getTargetIssueId())
                    .targetIssueNumber(target == null ? 0 : target.getIssueNumber())
                    .targetTitle(target == null ? null : target.getTitle())
                    .createdBy(link.getCreatedBy()).createdByName(names.get(link.getCreatedBy()))
                    .createdAt(link.getCreatedAt()).build());
        }
        return result;
    }

    private IssueSummaryResponse toSummary(Issue issue) {
        return IssueSummaryResponse.builder().id(issue.getId()).issueNumber(issue.getIssueNumber())
                .title(issue.getTitle()).status(issue.getStatus()).priority(issue.getPriority())
                .startDate(issue.getStartDate()).dueDate(issue.getDueDate()).build();
    }
}
