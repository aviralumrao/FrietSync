package com.frietsync.backend.service.invite;

import com.frietsync.backend.dto.invite.InviteRequest;
import com.frietsync.backend.dto.invite.InviteResponse;
import com.frietsync.backend.dto.project.ProjectInviteRequest;

import java.util.List;
import java.util.UUID;

public interface InviteService {
    InviteResponse createInvite(InviteRequest request, UUID adminId);
    InviteResponse createProjectInvite(UUID projectId, ProjectInviteRequest request, UUID inviterId);
    List<InviteResponse> myPendingInvites(UUID userId);
    InviteResponse acceptInvite(UUID userId, UUID inviteId);
    InviteResponse rejectInvite(UUID userId, UUID inviteId);
    InviteResponse revokeInvite(UUID adminId, UUID inviteId);
}