package com.frietsync.backend.service.impl.integration;

import com.frietsync.backend.config.integration.GitHubOAuthProperties;
import com.frietsync.backend.dto.integration.GitHubContributionResponse;
import com.frietsync.backend.dto.integration.ProjectGitHubLinkRequest;
import com.frietsync.backend.entity.integration.GitHubConnection;
import com.frietsync.backend.entity.integration.GitHubOAuthState;
import com.frietsync.backend.entity.project.Project;
import com.frietsync.backend.entity.project.ProjectGitHubRepository;
import com.frietsync.backend.entity.project.ProjectMember;
import com.frietsync.backend.entity.user.User;
import com.frietsync.backend.exception.BadRequestException;
import com.frietsync.backend.exception.ForbiddenException;
import com.frietsync.backend.exception.ResourceNotFoundException;
import com.frietsync.backend.repository.integration.GitHubConnectionRepository;
import com.frietsync.backend.repository.integration.GitHubOAuthStateRepository;
import com.frietsync.backend.repository.project.ProjectGitHubRepositoryRepository;
import com.frietsync.backend.repository.project.ProjectMemberRepository;
import com.frietsync.backend.repository.project.ProjectRepository;
import com.frietsync.backend.repository.user.UserRepository;
import com.frietsync.backend.service.integration.GitHubIntegrationService;
import com.frietsync.backend.service.impl.project.ProjectAccess;
import com.frietsync.backend.service.integration.GitHubApiClient;
import com.frietsync.backend.service.integration.GitHubTokenCipher;
import lombok.RequiredArgsConstructor;
import org.apache.commons.codec.digest.DigestUtils;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.web.util.UriComponentsBuilder;

import java.net.URI;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Base64;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class GitHubIntegrationServiceImpl implements GitHubIntegrationService {
    private final GitHubOAuthProperties properties;
    private final GitHubOAuthStateRepository stateRepository;
    private final GitHubConnectionRepository connectionRepository;
    private final ProjectGitHubRepositoryRepository projectRepositoryLink;
    private final ProjectRepository projectRepository;
    private final ProjectMemberRepository memberRepository;
    private final ProjectAccess projectAccess;
    private final UserRepository userRepository;
    private final GitHubTokenCipher cipher;
    private final GitHubApiClient githubApi;
    private final SecureRandom random = new SecureRandom();

    @Override
    @Transactional
    public URI createAuthorizationUri(UUID userId) {
        User user = userRepository.findById(userId).orElseThrow(() -> new ResourceNotFoundException("User not found"));
        byte[] bytes = new byte[32];
        random.nextBytes(bytes);
        String state = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
        GitHubOAuthState pending = new GitHubOAuthState();
        pending.setUser(user);
        pending.setStateHash(hash(state));
        pending.setExpiresAt(Instant.now().plusSeconds(properties.getStateTtlSeconds()));
        stateRepository.save(pending);
        return UriComponentsBuilder.fromUriString("https://github.com/login/oauth/authorize")
                .queryParam("client_id", properties.getClientId()).queryParam("redirect_uri", properties.getRedirectUri())
                .queryParam("scope", "read:user repo").queryParam("state", state).build().encode().toUri();
    }

    @Override
    @Transactional(noRollbackFor = BadRequestException.class)
    public UUID consumeState(String state) {
        if (state == null || state.length() > 200) throw new BadRequestException("Invalid GitHub OAuth state");
        GitHubOAuthState pending = stateRepository.findByHashForUpdate(hash(state))
                .orElseThrow(() -> new BadRequestException("Invalid GitHub OAuth state"));
        stateRepository.delete(pending);
        if (pending.getExpiresAt().isBefore(Instant.now())) throw new BadRequestException("Expired GitHub OAuth state");
        return pending.getUser().getId();
    }

    @Override
    @Transactional
    public void complete(UUID userId, String code) {
        LinkedMultiValueMap<String, String> form = new LinkedMultiValueMap<>();
        form.add("client_id", properties.getClientId());
        form.add("client_secret", properties.getClientSecret());
        form.add("code", code);
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_FORM_URLENCODED);
        headers.setAccept(List.of(MediaType.APPLICATION_JSON));
        Map<String, Object> tokenResult = githubApi.postForm("https://github.com/login/oauth/access_token",
                new HttpEntity<>(form, headers));
        if (tokenResult == null || !(tokenResult.get("access_token") instanceof String token)) {
            throw new BadRequestException("GitHub authorization failed");
        }
        Map<String, Object> profile = githubApi.getMap("/user", token);
        if (!(profile.get("login") instanceof String login)) throw new BadRequestException("GitHub did not return a username");
        GitHubConnection connection = connectionRepository.findByUser_Id(userId).orElseGet(GitHubConnection::new);
        connection.setUser(userRepository.getReferenceById(userId));
        connection.setGithubLogin(login);
        connection.setEncryptedAccessToken(cipher.encrypt(token));
        connectionRepository.save(connection);
    }

    @Override
    @Transactional
    public void disconnect(UUID userId) {
        connectionRepository.findByUser_Id(userId).ifPresent(connectionRepository::delete);
    }

    @Override
    @Transactional
    public void linkRepository(UUID projectId, UUID userId, ProjectGitHubLinkRequest request) {
        Project project = requireProject(projectId, userId, true);
        String owner = request.owner().trim();
        String name = request.repository().trim();
        GitHubConnection connection = connectionRepository.findByUser_Id(userId)
                .orElseThrow(() -> new BadRequestException("Connect GitHub before linking a repository"));
        String token = cipher.decrypt(connection.getEncryptedAccessToken());
        githubApi.getMap("/repos/" + owner + "/" + name, token);
        ProjectGitHubRepository linked = projectRepositoryLink.findByProjectId(project.getId())
                .orElseGet(ProjectGitHubRepository::new);
        linked.setProjectId(project.getId());
        linked.setRepositoryOwner(owner);
        linked.setRepositoryName(name);
        projectRepositoryLink.save(linked);
    }

    @Override
    @Transactional
    public void unlinkRepository(UUID projectId, UUID userId) {
        Project project = requireProject(projectId, userId, true);
        projectRepositoryLink.findByProjectId(project.getId()).ifPresent(projectRepositoryLink::delete);
    }

    @Override
    @Transactional(readOnly = true)
    public List<GitHubContributionResponse> contributions(UUID projectId, UUID userId) {
        Project project = requireProject(projectId, userId, false);
        ProjectGitHubRepository linked = projectRepositoryLink.findByProjectId(project.getId())
                .orElseThrow(() -> new ResourceNotFoundException("No GitHub repository is linked to this project"));
        GitHubConnection requester = connectionRepository.findByUser_Id(userId)
                .orElseThrow(() -> new BadRequestException("Connect GitHub to view repository contributions"));
        String token = cipher.decrypt(requester.getEncryptedAccessToken());
        String repoPath = "/repos/" + linked.getRepositoryOwner() + "/" + linked.getRepositoryName();
        List<Map<String, Object>> commits = githubPages(repoPath + "/commits?per_page=100", token);
        List<Map<String, Object>> pulls = githubPages(repoPath + "/pulls?state=all&per_page=100", token);
        Map<String, GitHubConnection> byLogin = connectionRepository.findAll().stream()
                .collect(Collectors.toMap(c -> c.getGithubLogin().toLowerCase(), c -> c, (a, b) -> a));
        Set<UUID> ids = new HashSet<>();
        ids.add(project.getAdminId());
        ids.add(project.getCreatedBy());
        if (project.getProjectManagerId() != null) ids.add(project.getProjectManagerId());
        if (project.getTeamLeadId() != null) ids.add(project.getTeamLeadId());
        memberRepository.findByProjectId(projectId).stream().map(ProjectMember::getUserId).forEach(ids::add);
        List<GitHubContributionResponse> result = new ArrayList<>();
        for (UUID id : ids) {
            User user = userRepository.findById(id).orElse(null);
            if (user == null) continue;
            GitHubConnection identity = byLogin.values().stream().filter(c -> c.getUser().getId().equals(id)).findFirst().orElse(null);
            if (identity == null) {
                result.add(new GitHubContributionResponse(id, user.getName(), null, 0, 0, 0, List.of()));
                continue;
            }
            String login = identity.getGithubLogin().toLowerCase();
            Map<String, Integer> perDay = new HashMap<>();
            int commitCount = 0;
            for (Map<String, Object> commit : commits) {
                Map<?, ?> author = object(commit.get("author"));
                if (author != null && login.equals(String.valueOf(author.get("login")).toLowerCase())) {
                    commitCount++;
                    Map<?, ?> commitData = object(commit.get("commit"));
                    Map<?, ?> committer = commitData == null ? null : object(commitData.get("committer"));
                    String date = committer == null ? null : String.valueOf(committer.get("date"));
                    if (date != null && date.length() >= 10) perDay.merge(date.substring(0, 10), 1, Integer::sum);
                }
            }
            int prs = 0;
            int merged = 0;
            for (Map<String, Object> pull : pulls) {
                Map<?, ?> author = object(pull.get("user"));
                if (author != null && login.equals(String.valueOf(author.get("login")).toLowerCase())) {
                    prs++;
                    if (pull.get("merged_at") != null) merged++;
                }
            }
            List<GitHubContributionResponse.GitHubContributionDay> days = perDay.entrySet().stream()
                    .sorted(Map.Entry.comparingByKey())
                    .map(e -> new GitHubContributionResponse.GitHubContributionDay(e.getKey(), e.getValue())).toList();
            result.add(new GitHubContributionResponse(id, user.getName(), identity.getGithubLogin(), commitCount, prs, merged, days));
        }
        return result;
    }

    private Project requireProject(UUID projectId, UUID userId, boolean managerOnly) {
        Project project = projectRepository.findById(projectId)
                .orElseThrow(() -> new ResourceNotFoundException("Project not found"));
        projectAccess.requireWorkspaceAndActive(project, userId);
        boolean member = userId.equals(project.getAdminId()) || userId.equals(project.getCreatedBy())
                || userId.equals(project.getProjectManagerId()) || userId.equals(project.getTeamLeadId())
                || memberRepository.existsByProjectIdAndUserId(projectId, userId);
        if (!member) throw new ForbiddenException("You don't have access to this project");
        if (managerOnly && !userId.equals(project.getAdminId()) && !userId.equals(project.getProjectManagerId())
                && !userId.equals(project.getTeamLeadId()) && !userId.equals(project.getCreatedBy())) {
            throw new ForbiddenException("Only a project manager or team lead can manage its GitHub repository");
        }
        return project;
    }

    private List<Map<String, Object>> githubPages(String path, String token) {
        List<Map<String, Object>> result = new ArrayList<>();
        for (int page = 1; page <= 10; page++) {
            String separator = path.contains("?") ? "&" : "?";
            List<Map<String, Object>> batch = githubApi.getList(path + separator + "page=" + page, token);
            if (batch == null || batch.isEmpty()) break;
            result.addAll(batch);
            if (batch.size() < 100) break;
        }
        return result;
    }

    private static Map<?, ?> object(Object value) {
        return value instanceof Map<?, ?> map ? map : null;
    }

    private static String hash(String value) { return DigestUtils.sha256Hex(value); }
}
