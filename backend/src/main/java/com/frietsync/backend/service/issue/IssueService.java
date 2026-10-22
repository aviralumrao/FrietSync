package com.frietsync.backend.service.issue;

import com.frietsync.backend.dto.issue.IssueHistoryResponse;
import com.frietsync.backend.dto.issue.IssueCreateV2Request;
import com.frietsync.backend.dto.issue.IssuePageResponse;
import com.frietsync.backend.dto.issue.IssueLabelRequest;
import com.frietsync.backend.dto.issue.IssueRequest;
import com.frietsync.backend.dto.issue.IssueResponse;
import com.frietsync.backend.dto.issue.IssueUpdateRequest;
import com.frietsync.backend.dto.issue.ReviewRequest;
import com.frietsync.backend.dto.project.LabelResponse;

import java.util.List;
import java.util.UUID;

public interface IssueService {
    IssueResponse create(UUID projectId, IssueRequest request, UUID userId);

    IssueResponse createV2(UUID projectId, IssueCreateV2Request request, UUID userId);

    IssuePageResponse<IssueResponse> listForProject(UUID projectId, UUID userId, int page, int size);

    IssuePageResponse<IssueResponse> listAssignedToMe(UUID userId, int page, int size);

    IssueResponse get(UUID issueId, UUID userId);

    IssueResponse update(UUID issueId, IssueUpdateRequest request, UUID userId);

    void delete(UUID issueId, UUID userId);

    IssueResponse resolve(UUID issueId, UUID userId);

    IssueResponse approveReview(UUID issueId, ReviewRequest request, UUID userId);

    IssueResponse rejectReview(UUID issueId, ReviewRequest request, UUID userId);

    IssuePageResponse<IssueHistoryResponse> history(UUID issueId, UUID userId, int page, int size);

    LabelResponse addLabel(UUID issueId, IssueLabelRequest request, UUID userId);

    void removeLabel(UUID issueId, UUID labelId, UUID userId);

    List<LabelResponse> labels(UUID issueId, UUID userId);

    void deleteAllForProject(UUID projectId);
}
