package com.frietsync.backend.controller.issue;

import com.frietsync.backend.dto.issue.DependencyGraphResponse;
import com.frietsync.backend.dto.issue.IssueLinkRequest;
import com.frietsync.backend.dto.issue.IssueLinkResponse;
import com.frietsync.backend.dto.issue.LinkedIssueResponse;
import com.frietsync.backend.service.issue.IssueLinkService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
public class IssueLinkController {

    private final IssueLinkService linkService;

    @PostMapping("/issues/{issueId}/links")
    public ResponseEntity<IssueLinkResponse> create(@PathVariable UUID issueId,
                                                    @Valid @RequestBody IssueLinkRequest request,
                                                    @AuthenticationPrincipal UUID userId) {
        return ResponseEntity.status(HttpStatus.CREATED).body(linkService.create(issueId, request, userId));
    }

    @GetMapping("/issues/{issueId}/links")
    public ResponseEntity<List<LinkedIssueResponse>> list(@PathVariable UUID issueId,
                                                        @AuthenticationPrincipal UUID userId) {
        return ResponseEntity.ok(linkService.list(issueId, userId));
    }

    @DeleteMapping("/issue-links/{linkId}")
    public ResponseEntity<Void> delete(@PathVariable UUID linkId,
                                       @AuthenticationPrincipal UUID userId) {
        linkService.delete(linkId, userId);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/projects/{projectId}/dependency-graph")
    public ResponseEntity<DependencyGraphResponse> dependencyGraph(@PathVariable UUID projectId,
                                                                   @AuthenticationPrincipal UUID userId) {
        return ResponseEntity.ok(linkService.dependencyGraph(projectId, userId));
    }
}