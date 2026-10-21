package com.frietsync.backend.service.impl.employeerecord;

import com.frietsync.backend.entity.integration.GitHubConnection;
import com.frietsync.backend.entity.user.User;
import com.frietsync.backend.repository.integration.GitHubConnectionRepository;
import com.frietsync.backend.repository.project.ProjectGitHubRepositoryRepository;
import com.frietsync.backend.service.employeerecord.GithubActivity;
import com.frietsync.backend.service.employeerecord.GithubActivityProvider;
import com.frietsync.backend.service.employeerecord.GithubRepoActivity;
import com.frietsync.backend.service.integration.GitHubApiClient;
import com.frietsync.backend.service.integration.GitHubTokenCipher;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClientException;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Component
@RequiredArgsConstructor
public class GithubActivityProviderImpl implements GithubActivityProvider {

    private static final Logger LOG = LoggerFactory.getLogger(GithubActivityProviderImpl.class);

    private final GitHubConnectionRepository connectionRepository;
    private final ProjectGitHubRepositoryRepository linkRepository;
    private final GitHubTokenCipher cipher;
    private final GitHubApiClient githubApi;

    @Override
    public GithubActivity fetch(User user, Collection<UUID> projectIds) {
        GitHubConnection connection = connectionRepository.findByUser_Id(user.getId()).orElse(null);
        if (connection == null) {
            return GithubActivity.notConnected();
        }
        String login = connection.getGithubLogin();
        String token = cipher.decrypt(connection.getEncryptedAccessToken());

        Map<String, String> repositories = new LinkedHashMap<>();
        for (UUID projectId : projectIds) {
            linkRepository.findByProjectId(projectId).ifPresent(link -> {
                String name = link.getRepositoryOwner() + "/" + link.getRepositoryName();
                repositories.putIfAbsent(name.toLowerCase(), name);
            });
        }

        List<GithubRepoActivity> perRepository = new ArrayList<>();
        int commits = 0;
        int pullRequests = 0;
        int merged = 0;
        int reviews = 0;
        int failed = 0;
        for (String repository : repositories.values()) {
            try {
                String scope = "+repo:" + repository;
                int repoCommits = total("/search/commits?q=author:" + login + scope, token);
                int repoPulls = total("/search/issues?q=type:pr+author:" + login + scope, token);
                int repoMerged = total("/search/issues?q=type:pr+is:merged+author:" + login + scope, token);
                int repoReviews = total("/search/issues?q=type:pr+reviewed-by:" + login + scope, token);
                perRepository.add(new GithubRepoActivity(repository, repoCommits, repoPulls, repoMerged, repoReviews));
                commits += repoCommits;
                pullRequests += repoPulls;
                merged += repoMerged;
                reviews += repoReviews;
            } catch (RestClientException ex) {
                failed++;
                LOG.warn("GitHub activity lookup failed for {}: {}", repository, ex.getMessage());
            }
        }
        return new GithubActivity(true, login, commits, pullRequests, merged, reviews, failed, perRepository);
    }

    private int total(String path, String token) {
        Map<String, Object> body = githubApi.getMap(path, token);
        if (body != null && body.get("total_count") instanceof Number count) {
            return count.intValue();
        }
        return 0;
    }
}