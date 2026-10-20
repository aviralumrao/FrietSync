package com.frietsync.backend.controller.integration;

import com.frietsync.backend.config.integration.GitHubOAuthProperties;
import com.frietsync.backend.dto.integration.GitHubContributionResponse;
import com.frietsync.backend.dto.integration.ProjectGitHubLinkRequest;
import com.frietsync.backend.service.integration.GitHubIntegrationService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.util.UriComponentsBuilder;

import java.net.URI;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/integrations/github")
@RequiredArgsConstructor
public class GitHubIntegrationController {
    private final GitHubIntegrationService service;
    private final GitHubOAuthProperties properties;

    @GetMapping("/connect")
    public ResponseEntity<Map<String, String>> connect(@AuthenticationPrincipal UUID userId) {
        return ResponseEntity.ok(Map.of("authorizationUrl", service.createAuthorizationUri(userId).toString()));
    }

    @GetMapping("/callback")
    public ResponseEntity<Void> callback(String state, String code, String error) {
        UUID userId = service.consumeState(state);
        if (error != null) return redirect("denied");
        if (code == null || code.isBlank()) return redirect("failed");
        service.complete(userId, code);
        return redirect("connected");
    }

    @DeleteMapping("/disconnect")
    public ResponseEntity<Void> disconnect(@AuthenticationPrincipal UUID userId) {
        service.disconnect(userId);
        return ResponseEntity.noContent().build();
    }

    @PutMapping("/projects/{projectId}/repository")
    public ResponseEntity<Void> link(@PathVariable UUID projectId, @Valid @RequestBody ProjectGitHubLinkRequest request,
                                     @AuthenticationPrincipal UUID userId) {
        service.linkRepository(projectId, userId, request);
        return ResponseEntity.noContent().build();
    }

    @DeleteMapping("/projects/{projectId}/repository")
    public ResponseEntity<Void> unlink(@PathVariable UUID projectId, @AuthenticationPrincipal UUID userId) {
        service.unlinkRepository(projectId, userId);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/projects/{projectId}/contributions")
    public ResponseEntity<List<GitHubContributionResponse>> contributions(@PathVariable UUID projectId,
                                                                           @AuthenticationPrincipal UUID userId) {
        return ResponseEntity.ok(service.contributions(projectId, userId));
    }

    private ResponseEntity<Void> redirect(String result) {
        URI target = UriComponentsBuilder.fromUriString(properties.getFrontendResultUri())
                .queryParam("github", result).build().encode().toUri();
        return ResponseEntity.status(HttpStatus.FOUND).location(target).build();
    }
}
