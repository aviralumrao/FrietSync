package com.frietsync.backend.service.invite;

import com.frietsync.backend.dto.invite.InviteRequest;
import com.frietsync.backend.dto.invite.InviteResponse;

import java.util.List;
import java.util.UUID;

public interface InviteService {
    InviteResponse createInvite(InviteRequest request, UUID adminId);
    List<InviteResponse> myPendingInvites(UUID userId);
    InviteResponse acceptInvite(UUID userId, UUID inviteId);
    InviteResponse rejectInvite(UUID userId, UUID inviteId);
    InviteResponse revokeInvite(UUID adminId, UUID inviteId);
}