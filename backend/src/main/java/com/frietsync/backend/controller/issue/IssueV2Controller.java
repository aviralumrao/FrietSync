package com.frietsync.backend.controller.issue;

import com.frietsync.backend.dto.issue.IssueCreateV2Request;
import com.frietsync.backend.dto.issue.IssueResponse;
import com.frietsync.backend.service.issue.IssueService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/api/v2/projects/{projectId}/issues")
@RequiredArgsConstructor
public class IssueV2Controller {
    private final IssueService issueService;

    @PostMapping
    public ResponseEntity<IssueResponse> create(@PathVariable UUID projectId,
                                                @Valid @RequestBody IssueCreateV2Request request,
                                                @AuthenticationPrincipal UUID userId) {
        return ResponseEntity.status(HttpStatus.CREATED).body(issueService.createV2(projectId, request, userId));
    }
}
