package com.frietsync.backend.service.issue;

import com.frietsync.backend.dto.issue.DependencyGraphResponse;
import com.frietsync.backend.dto.issue.IssueLinkRequest;
import com.frietsync.backend.dto.issue.IssueLinkResponse;
import com.frietsync.backend.dto.issue.LinkedIssueResponse;

import java.util.List;
import java.util.UUID;

public interface IssueLinkService {
    IssueLinkResponse create(UUID issueId, IssueLinkRequest request, UUID userId);

    List<LinkedIssueResponse> list(UUID issueId, UUID userId);

    void delete(UUID linkId, UUID userId);

    DependencyGraphResponse dependencyGraph(UUID projectId, UUID userId);
}