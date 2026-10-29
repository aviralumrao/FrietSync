package com.frietsync.backend.service.issue;

import com.frietsync.backend.dto.issue.AssignIssueRequest;
import com.frietsync.backend.dto.issue.AssignmentDecisionRequest;
import com.frietsync.backend.dto.issue.AssignmentRequestResponse;
import com.frietsync.backend.dto.issue.IssueResponse;
import com.frietsync.backend.entity.issue.AssignmentRequestStatus;

import java.util.List;
import java.util.UUID;

public interface IssueAssignmentService {
    IssueResponse assign(UUID issueId, AssignIssueRequest request, UUID userId);

    AssignmentRequestResponse requestAssignment(UUID issueId, UUID userId);

    AssignmentRequestResponse myRequest(UUID issueId, UUID userId);

    void cancelMyRequest(UUID issueId, UUID userId);

    List<AssignmentRequestResponse> listRequests(UUID projectId, AssignmentRequestStatus status, UUID userId);

    AssignmentRequestResponse decide(UUID issueId, AssignmentDecisionRequest request, UUID userId);

    AssignmentRequestResponse rejectRequest(UUID projectId, UUID requestId, UUID userId);
}
