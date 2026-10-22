package com.frietsync.backend.service.impl.integration;

import com.frietsync.backend.dto.issue.IssueResponse;
import com.frietsync.backend.entity.integration.GitHubConnection;
import com.frietsync.backend.entity.issue.Issue;
import com.frietsync.backend.entity.project.ProjectGitHubRepository;
import com.frietsync.backend.entity.project.Project;
import com.frietsync.backend.entity.user.Role;
import com.frietsync.backend.exception.BadRequestException;
import com.frietsync.backend.exception.ForbiddenException;
import com.frietsync.backend.exception.ResourceNotFoundException;
import com.frietsync.backend.repository.integration.GitHubConnectionRepository;
import com.frietsync.backend.repository.issue.IssueRepository;
import com.frietsync.backend.repository.project.ProjectGitHubRepositoryRepository;
import com.frietsync.backend.service.integration.GitHubApiClient;
import com.frietsync.backend.service.integration.GitHubIssueLinkService;
import com.frietsync.backend.service.integration.GitHubTokenCipher;
import com.frietsync.backend.service.impl.issue.IssueMapper;
import com.frietsync.backend.service.impl.project.ProjectAccess;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class GitHubIssueLinkServiceImpl implements GitHubIssueLinkService {
    private final IssueRepository issueRepository;
    private final ProjectGitHubRepositoryRepository projectRepositoryLink;
    private final GitHubConnectionRepository connectionRepository;
    private final GitHubTokenCipher cipher;
    private final GitHubApiClient githubApi;
    private final ProjectAccess projectAccess;
    private final IssueMapper issueMapper;

    @Override
    @Transactional
    public IssueResponse link(UUID issueId, int githubIssueNumber, UUID userId) {
        Issue issue = projectAccess.issue(issueId);
        Project project = projectAccess.project(issue.getProjectId());
        requireLinkPermission(project, userId);
        ProjectGitHubRepository linked = projectRepositoryLink.findByProjectId(project.getId())
                .orElseThrow(() -> new BadRequestException("Link a GitHub repository to the project first"));
        if (githubIssueNumber < 1) {
            throw new BadRequestException("Invalid GitHub issue number");
        }
        validateRepository(linked);
        GitHubConnection connection = connectionRepository.findByUser_Id(userId)
                .orElseThrow(() -> new BadRequestException("Connect GitHub before linking an issue"));
        String repository = linked.getRepositoryOwner() + "/" + linked.getRepositoryName();
        Map<String, Object> response = githubApi.getMap("/repos/" + repository + "/issues/" + githubIssueNumber,
                cipher.decrypt(connection.getEncryptedAccessToken()));
        if (response == null || !(response.get("number") instanceof Number number)
                || !(response.get("title") instanceof String title)
                || !(response.get("html_url") instanceof String url)) {
            throw new ResourceNotFoundException("GitHub issue not found in the project's linked repository");
        }
        if (response.containsKey("pull_request")) {
            throw new BadRequestException("Only GitHub issues can be linked, not pull requests");
        }
        issue.setGithubIssueNumber(number.intValue());
        issue.setGithubIssueTitle(title);
        issue.setGithubIssueUrl(url);
        issue.setGithubIssueRepository(repository);
        return issueMapper.toResponse(issueRepository.save(issue));
    }

    @Override
    @Transactional
    public IssueResponse createAndLink(UUID issueId, UUID userId) {
        Issue issue = projectAccess.issue(issueId);
        Project project = projectAccess.project(issue.getProjectId());
        requireLinkPermission(project, userId);
        ProjectGitHubRepository linked = projectRepositoryLink.findByProjectId(project.getId())
                .orElseThrow(() -> new BadRequestException("Link a GitHub repository to the project first"));
        validateRepository(linked);
        GitHubConnection connection = connectionRepository.findByUser_Id(userId)
                .orElseThrow(() -> new BadRequestException("Connect GitHub before creating an issue"));
        Map<String, Object> body = new HashMap<>();
        body.put("title", issue.getTitle());
        body.put("body", issue.getDescription());
        String repository = linked.getRepositoryOwner() + "/" + linked.getRepositoryName();
        Map<String, Object> response = githubApi.postMap("/repos/" + repository + "/issues", body,
                cipher.decrypt(connection.getEncryptedAccessToken()));
        if (response == null || !(response.get("number") instanceof Number number)
                || !(response.get("title") instanceof String title)
                || !(response.get("html_url") instanceof String url)) {
            throw new BadRequestException("GitHub did not return the created issue details");
        }
        issue.setGithubIssueNumber(number.intValue());
        issue.setGithubIssueTitle(title);
        issue.setGithubIssueUrl(url);
        issue.setGithubIssueRepository(repository);
        return issueMapper.toResponse(issueRepository.save(issue));
    }

    @Override
    @Transactional
    public void unlink(UUID issueId, UUID userId) {
        Issue issue = projectAccess.issue(issueId);
        Project project = projectAccess.project(issue.getProjectId());
        requireLinkPermission(project, userId);
        issue.setGithubIssueNumber(null);
        issue.setGithubIssueTitle(null);
        issue.setGithubIssueUrl(null);
        issue.setGithubIssueRepository(null);
        issueRepository.save(issue);
    }

    private void requireLinkPermission(Project project, UUID userId) {
        Role role = projectAccess.requireMember(project, userId);
        if (role != Role.ADMIN && role != Role.PROJECT_MANAGER && role != Role.TEAM_LEAD && role != Role.REPORTER) {
            throw new ForbiddenException("Only project admins, project managers, team leads, and reviewers can manage GitHub issue links");
        }
    }

    private void validateRepository(ProjectGitHubRepository linked) {
        if (!linked.getRepositoryOwner().matches("[A-Za-z0-9-]+")
                || !linked.getRepositoryName().matches("[A-Za-z0-9._-]+")) {
            throw new BadRequestException("Invalid linked GitHub repository");
        }
    }
}
