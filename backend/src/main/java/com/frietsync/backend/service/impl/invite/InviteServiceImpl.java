package com.frietsync.backend.service.impl.invite;

import com.frietsync.backend.exception.BadRequestException;
import com.frietsync.backend.service.email.EmailService;
import com.frietsync.backend.dto.invite.InviteRequest;
import com.frietsync.backend.dto.invite.InviteResponse;
import com.frietsync.backend.entity.invite.Invite;
import com.frietsync.backend.entity.invite.InviteStatus;
import com.frietsync.backend.repository.invite.InviteRepository;
import com.frietsync.backend.service.invite.InviteService;
import com.frietsync.backend.entity.user.User;
import com.frietsync.backend.entity.user.Role;
import com.frietsync.backend.repository.user.UserRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Service
public class InviteServiceImpl implements InviteService {

    private static final Duration INVITE_VALIDITY = Duration.ofDays(7);

    private final InviteRepository inviteRepository;
    private final UserRepository userRepository;
    private final EmailService emailService;

    public InviteServiceImpl(InviteRepository inviteRepository,
                             UserRepository userRepository,
                             EmailService emailService) {
        this.inviteRepository = inviteRepository;
        this.userRepository = userRepository;
        this.emailService = emailService;
    }

    @Override
    @Transactional
    public InviteResponse createInvite(InviteRequest request, UUID adminId) {
        if (request.getEmail() == null || request.getEmail().isBlank()) {
            throw new BadRequestException("Email is required");
        }

        Instant now = Instant.now();
        Instant expiresAt = request.getExpiresAt() == null
                ? now.plus(INVITE_VALIDITY)
                : request.getExpiresAt();
        if (!expiresAt.isAfter(now)) {
            throw new BadRequestException(
                    "Invite expiration time must be in the future"
            );
        }

        if (request.getRole() == null || request.getRole() == Role.ADMIN) {
            throw new BadRequestException(
                    "Invite role must be PROJECT_MANAGER, TEAM_LEAD, CONTRIBUTOR, or REPORTER"
            );
        }
        String email = request.getEmail().trim().toLowerCase();

        User admin = userRepository.findById(adminId)
                .orElseThrow(() -> new BadRequestException("Admin not found"));

        if (admin.getRole() != Role.ADMIN) {
            throw new BadRequestException(
                    "Only admins can send invitations"
            );
        }
        if (email.equalsIgnoreCase(admin.getEmail())) {
            throw new BadRequestException(
                    "You cannot invite yourself"
            );
        }
        ensureWorkspaceId(admin);

        Invite invite = new Invite();
        invite.setEmail(email);
        invite.setRole(request.getRole());
        invite.setStatus(InviteStatus.PENDING);
        invite.setInvitedBy(admin.getId());
        invite.setExpiresAt(expiresAt);
        invite.setWorkspaceId(admin.getWorkspaceId());

        inviteRepository.save(invite);

        emailService.sendInviteMail(email, invite.getRole().name());

        return toResponse(invite);
    }

    @Override
    @Transactional
    public List<InviteResponse> myPendingInvites(UUID userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new BadRequestException("User not found"));

        List<Invite> invites = inviteRepository
                .findByEmailAndStatusOrderByCreatedAtDesc(
                        user.getEmail(),
                        InviteStatus.PENDING
                );

        List<InviteResponse> responses = new ArrayList<>();
        List<Invite> expiredInvites = new ArrayList<>();

        for (Invite invite : invites) {
            if (isExpired(invite, Instant.now())) {
                invite.setStatus(InviteStatus.EXPIRED);
                expiredInvites.add(invite);
            } else {
                responses.add(toResponse(invite));
            }
        }
        if (!expiredInvites.isEmpty()) {
            inviteRepository.saveAll(expiredInvites);
        }

        return responses;
    }

    private InviteResponse toResponse(Invite i) {
        InviteResponse r = new InviteResponse();
        r.setId(i.getId());
        r.setEmail(i.getEmail());
        r.setRole(i.getRole());
        r.setStatus(i.getStatus());

        User inviter = userRepository.findById(i.getInvitedBy())
                .orElseThrow(() -> new BadRequestException("Inviting user not found"));

        r.setInvitedByName(inviter.getName());
        r.setInvitedByEmail(inviter.getEmail());
        r.setWorkspaceId(i.getWorkspaceId());
        r.setCreatedAt(i.getCreatedAt());
        r.setAcceptedAt(i.getAcceptedAt());
        r.setExpiresAt(getExpirationTime(i));
        return r;
    }

    @Override
    @Transactional
    public InviteResponse acceptInvite(UUID userId, UUID inviteId) {
        if (inviteId == null) {
            throw new BadRequestException("inviteId is required");
        }

        User user = userRepository.findById(userId)
                .orElseThrow(() -> new BadRequestException("User not found"));

        Invite invite = inviteRepository.findByIdForUpdate(inviteId)
                .orElseThrow(() -> new BadRequestException("Invite not found"));

        if (!invite.getEmail().equalsIgnoreCase(user.getEmail())) {
            throw new BadRequestException(
                    "This invite does not belong to your account"
            );
        }

        if (invite.getStatus() != InviteStatus.PENDING) {
            throw new BadRequestException("This invite is no longer pending");
        }

        if (isExpired(invite, Instant.now())) {
            throw new BadRequestException("This invite has expired");
        }

        if (user.getWorkspaceId() != null
                && !user.getWorkspaceId().equals(invite.getWorkspaceId())) {
            throw new BadRequestException(
                    "You are already a member of another workspace, Leave it to join this"
            );
        }

        user.setWorkspaceId(invite.getWorkspaceId());
        user.setRole(invite.getRole());
        userRepository.save(user);

        invite.setStatus(InviteStatus.ACCEPTED);
        invite.setAcceptedAt(Instant.now());
        inviteRepository.save(invite);

        return toResponse(invite);
    }

    @Override
    @Transactional
    public InviteResponse rejectInvite(UUID userId, UUID inviteId) {
        if (inviteId == null) {
            throw new BadRequestException("inviteId is required");
        }

        User user = userRepository.findById(userId)
                .orElseThrow(() -> new BadRequestException("User not found"));
        Invite invite = inviteRepository.findByIdForUpdate(inviteId)
                .orElseThrow(() -> new BadRequestException("Invite not found"));

        if (!invite.getEmail().equalsIgnoreCase(user.getEmail())) {
            throw new BadRequestException("This invite does not belong to your account");
        }
        ensurePendingAndNotExpired(invite);

        invite.setStatus(InviteStatus.REJECTED);
        inviteRepository.save(invite);
        return toResponse(invite);
    }

    @Override
    @Transactional
    public InviteResponse revokeInvite(UUID inviteId) {
        if (inviteId == null) {
            throw new BadRequestException("inviteId is required");
        }

        Invite invite = inviteRepository.findByIdForUpdate(inviteId)
                .orElseThrow(() -> new BadRequestException("Invite not found"));

        ensurePendingAndNotExpired(invite);

        invite.setStatus(InviteStatus.REVOKED);
        inviteRepository.save(invite);
        return toResponse(invite);
    }

    private void ensurePendingAndNotExpired(Invite invite) {
        if (invite.getStatus() != InviteStatus.PENDING) {
            throw new BadRequestException("This invite is no longer pending");
        }
        if (isExpired(invite, Instant.now())) {
            throw new BadRequestException("This invite has expired");
        }
    }

    private boolean isExpired(Invite invite, Instant now) {
        return !now.isBefore(getExpirationTime(invite));
    }

    private Instant getExpirationTime(Invite invite) {
        return invite.getExpiresAt() != null
                ? invite.getExpiresAt()
                : invite.getCreatedAt().plus(INVITE_VALIDITY);
    }

    private void ensureWorkspaceId(User user) {
        if (user.getWorkspaceId() == null) {
            user.setWorkspaceId(UUID.randomUUID());
            userRepository.save(user);
        }
    }
}