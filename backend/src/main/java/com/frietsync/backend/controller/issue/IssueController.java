package com.frietsync.backend.controller.issue;

import com.frietsync.backend.dto.issue.IssuePageResponse;
import com.frietsync.backend.dto.issue.ChangeIssueStatusRequest;
import com.frietsync.backend.dto.issue.GitHubIssueLinkRequest;
import com.frietsync.backend.dto.issue.IssueHistoryResponse;
import com.frietsync.backend.dto.issue.IssueLabelRequest;
import com.frietsync.backend.dto.issue.IssueRequest;
import com.frietsync.backend.dto.issue.IssueResponse;
import com.frietsync.backend.dto.issue.IssueUpdateRequest;
import com.frietsync.backend.dto.issue.ReviewRequest;
import com.frietsync.backend.dto.project.LabelResponse;
import com.frietsync.backend.service.issue.IssueService;
import com.frietsync.backend.service.integration.GitHubIssueLinkService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
public class IssueController {

    private final IssueService issueService;
    private final GitHubIssueLinkService githubIssueLinkService;

    @PutMapping("/issues/{issueId}/github-issue")
    public ResponseEntity<IssueResponse> linkGitHubIssue(@PathVariable UUID issueId,
                                                         @Valid @RequestBody GitHubIssueLinkRequest request,
                                                         @AuthenticationPrincipal UUID userId) {
        return ResponseEntity.ok(githubIssueLinkService.link(issueId, request.issueNumber(), userId));
    }

    @DeleteMapping("/issues/{issueId}/github-issue")
    public ResponseEntity<Void> unlinkGitHubIssue(@PathVariable UUID issueId,
                                                  @AuthenticationPrincipal UUID userId) {
        githubIssueLinkService.unlink(issueId, userId);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/projects/{projectId}/issues")
    public ResponseEntity<IssueResponse> create(@PathVariable UUID projectId,
                                                @Valid @RequestBody IssueRequest request,
                                                @AuthenticationPrincipal UUID userId) {
        return ResponseEntity.status(HttpStatus.CREATED).body(issueService.create(projectId, request, userId));
    }

    @GetMapping("/projects/{projectId}/issues")
    public ResponseEntity<IssuePageResponse<IssueResponse>> list(@PathVariable UUID projectId,
                                                    @RequestParam(defaultValue = "0") int page,
                                                    @RequestParam(defaultValue = "20") int size,
                                                    @AuthenticationPrincipal UUID userId) {
        return ResponseEntity.ok(issueService.listForProject(projectId, userId, page, size));
    }

    @GetMapping("/issues/me")
    public ResponseEntity<List<IssueResponse>> assignedToMe(
            @AuthenticationPrincipal UUID userId) {
        return ResponseEntity.ok(issueService.listAssignedToMe(userId));
    }

    @GetMapping("/issues/{issueId}")
    public ResponseEntity<IssueResponse> get(@PathVariable UUID issueId,
                                             @AuthenticationPrincipal UUID userId) {
        return ResponseEntity.ok(issueService.get(issueId, userId));
    }

    @PatchMapping("/issues/{issueId}")
    public ResponseEntity<IssueResponse> update(@PathVariable UUID issueId,
                                                @Valid @RequestBody IssueUpdateRequest request,
                                                @AuthenticationPrincipal UUID userId) {
        return ResponseEntity.ok(issueService.update(issueId, request, userId));
    }

    @PatchMapping("/issues/{issueId}/status")
    public ResponseEntity<IssueResponse> changeStatus(@PathVariable UUID issueId,
                                                       @Valid @RequestBody ChangeIssueStatusRequest request,
                                                       @AuthenticationPrincipal UUID userId) {
        return ResponseEntity.ok(issueService.changeStatus(issueId, request, userId));
    }

    @DeleteMapping("/issues/{issueId}")
    public ResponseEntity<Void> delete(@PathVariable UUID issueId,
                                       @AuthenticationPrincipal UUID userId) {
        issueService.delete(issueId, userId);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/issues/{issueId}/resolve")
    public ResponseEntity<IssueResponse> resolve(@PathVariable UUID issueId,
                                                 @AuthenticationPrincipal UUID userId) {
        return ResponseEntity.ok(issueService.resolve(issueId, userId));
    }

    @GetMapping("/issues/{issueId}/history")
    public ResponseEntity<IssuePageResponse<IssueHistoryResponse>> history(@PathVariable UUID issueId,
                                                              @RequestParam(defaultValue = "0") int page,
                                                              @RequestParam(defaultValue = "20") int size,
                                                              @AuthenticationPrincipal UUID userId) {
        return ResponseEntity.ok(issueService.history(issueId, userId, page, size));
    }

    @PostMapping("/issues/{issueId}/review/approve")
    public ResponseEntity<IssueResponse> approve(@PathVariable UUID issueId,
                                                 @Valid @RequestBody(required = false) ReviewRequest request,
                                                 @AuthenticationPrincipal UUID userId) {
        return ResponseEntity.ok(issueService.approveReview(issueId, request, userId));
    }

    @PostMapping("/issues/{issueId}/review/reject")
    public ResponseEntity<IssueResponse> reject(@PathVariable UUID issueId,
                                                @Valid @RequestBody(required = false) ReviewRequest request,
                                                @AuthenticationPrincipal UUID userId) {
        return ResponseEntity.ok(issueService.rejectReview(issueId, request, userId));
    }

    @PostMapping("/issues/{issueId}/labels")
    public ResponseEntity<LabelResponse> addLabel(@PathVariable UUID issueId,
                                                  @Valid @RequestBody IssueLabelRequest request,
                                                  @AuthenticationPrincipal UUID userId) {
        return ResponseEntity.status(HttpStatus.CREATED).body(issueService.addLabel(issueId, request, userId));
    }

    @GetMapping("/issues/{issueId}/labels")
    public ResponseEntity<List<LabelResponse>> labels(@PathVariable UUID issueId,
                                                      @AuthenticationPrincipal UUID userId) {
        return ResponseEntity.ok(issueService.labels(issueId, userId));
    }

    @DeleteMapping("/issues/{issueId}/labels/{labelId}")
    public ResponseEntity<Void> removeLabel(@PathVariable UUID issueId,
                                            @PathVariable UUID labelId,
                                            @AuthenticationPrincipal UUID userId) {
        issueService.removeLabel(issueId, labelId, userId);
        return ResponseEntity.noContent().build();
    }
}
