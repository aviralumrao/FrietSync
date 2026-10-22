package com.frietsync.backend.service.impl.issue;

import com.frietsync.backend.dto.issue.IssueResponse;
import com.frietsync.backend.dto.issue.GitHubIssueResponse;
import com.frietsync.backend.entity.issue.Issue;
import com.frietsync.backend.entity.issue.IssueAssignee;
import com.frietsync.backend.repository.issue.IssueAssigneeRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

@Component
@RequiredArgsConstructor
public class IssueMapper {

    private final IssueAssigneeRepository assigneeRepository;

    public IssueResponse toResponse(Issue issue) {
        return toResponses(List.of(issue)).get(0);
    }

    public List<IssueResponse> toResponses(List<Issue> issues) {
        if (issues.isEmpty()) {
            return List.of();
        }
        Map<UUID, List<UUID>> assignees = assigneeRepository
                .findByIssueIdIn(issues.stream().map(Issue::getId).toList())
                .stream()
                .collect(Collectors.groupingBy(IssueAssignee::getIssueId,
                        Collectors.mapping(IssueAssignee::getUserId, Collectors.toList())));
        return issues.stream()
                .map(i -> build(i, assignees.getOrDefault(i.getId(), List.of())))
                .toList();
    }

    private IssueResponse build(Issue i, List<UUID> assigneeIds) {
        return IssueResponse.builder()
                .id(i.getId())
                .projectId(i.getProjectId())
                .issueNumber(i.getIssueNumber())
                .title(i.getTitle())
                .description(i.getDescription())
                .status(i.getStatus())
                .priority(i.getPriority())
                .dueDate(i.getDueDate())
                .estimatedHours(i.getEstimatedHours())
                .createdBy(i.getCreatedBy())
                .assigneeIds(assigneeIds)
                .resolvedAt(i.getResolvedAt())
                .closedAt(i.getClosedAt())
                .createdAt(i.getCreatedAt())
                .updatedAt(i.getUpdatedAt())
                .githubIssue(i.getGithubIssueNumber() == null ? null : GitHubIssueResponse.builder()
                        .number(i.getGithubIssueNumber())
                        .title(i.getGithubIssueTitle())
                        .url(i.getGithubIssueUrl())
                        .repository(i.getGithubIssueRepository())
                        .build())
                .build();
    }
}