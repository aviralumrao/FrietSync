package com.frietsync.backend.controller.issue;

import com.frietsync.backend.dto.issue.AssignIssueRequest;
import com.frietsync.backend.dto.issue.AssignmentDecisionRequest;
import com.frietsync.backend.dto.issue.AssignmentRequestResponse;
import com.frietsync.backend.dto.issue.IssueResponse;
import com.frietsync.backend.entity.issue.AssignmentRequestStatus;
import com.frietsync.backend.service.issue.IssueAssignmentService;
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
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
public class IssueAssignmentController {

    private final IssueAssignmentService assignmentService;

    @PostMapping("/issues/{issueId}/assign")
    public ResponseEntity<IssueResponse> assign(@PathVariable UUID issueId,
                                                @Valid @RequestBody AssignIssueRequest request,
                                                @AuthenticationPrincipal UUID userId) {
        return ResponseEntity.ok(assignmentService.assign(issueId, request, userId));
    }

    @PostMapping("/issues/{issueId}/request")
    public ResponseEntity<AssignmentRequestResponse> requestAssignment(
            @PathVariable UUID issueId,
            @AuthenticationPrincipal UUID userId) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(assignmentService.requestAssignment(issueId, userId));
    }

    @GetMapping("/issues/{issueId}/request")
    public ResponseEntity<AssignmentRequestResponse> myRequest(@PathVariable UUID issueId,
                                                               @AuthenticationPrincipal UUID userId) {
        return ResponseEntity.ok(assignmentService.myRequest(issueId, userId));
    }

    @DeleteMapping("/issues/{issueId}/request")
    public ResponseEntity<Void> cancelMyRequest(@PathVariable UUID issueId,
                                                @AuthenticationPrincipal UUID userId) {
        assignmentService.cancelMyRequest(issueId, userId);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/projects/{projectId}/issue-requests")
    public ResponseEntity<List<AssignmentRequestResponse>> listRequests(
            @PathVariable UUID projectId,
            @RequestParam(required = false) AssignmentRequestStatus status,
            @AuthenticationPrincipal UUID userId) {
        return ResponseEntity.ok(assignmentService.listRequests(projectId, status, userId));
    }

    @PostMapping("/issues/{issueId}/issue-requests")
    public ResponseEntity<AssignmentRequestResponse> decide(@PathVariable UUID issueId,
                                                            @Valid @RequestBody AssignmentDecisionRequest request,
                                                            @AuthenticationPrincipal UUID userId) {
        return ResponseEntity.ok(assignmentService.decide(issueId, request, userId));
    }

    @DeleteMapping("/projects/{projectId}/issue-requests")
    public ResponseEntity<AssignmentRequestResponse> rejectRequest(@PathVariable UUID projectId,
                                                                   @RequestParam UUID requestId,
                                                                   @AuthenticationPrincipal UUID userId) {
        return ResponseEntity.ok(assignmentService.rejectRequest(projectId, requestId, userId));
    }
}