package com.frietsync.backend.service.impl.invite;

import com.frietsync.backend.exception.BadRequestException;
import com.frietsync.backend.exception.ForbiddenException;
import com.frietsync.backend.service.email.EmailService;
import com.frietsync.backend.dto.invite.InviteRequest;
import com.frietsync.backend.dto.invite.InviteResponse;
import com.frietsync.backend.dto.project.ProjectInviteRequest;
import com.frietsync.backend.entity.invite.Invite;
import com.frietsync.backend.entity.invite.InvitePurpose;
import com.frietsync.backend.entity.invite.InviteStatus;
import com.frietsync.backend.entity.project.Project;
import com.frietsync.backend.entity.project.ProjectMember;
import com.frietsync.backend.entity.workspace.Workspace;
import com.frietsync.backend.repository.invite.InviteRepository;
import com.frietsync.backend.repository.project.ProjectMemberRepository;
import com.frietsync.backend.repository.project.ProjectRepository;
import com.frietsync.backend.repository.workspace.WorkspaceRepository;
import com.frietsync.backend.service.invite.InviteService;
import com.frietsync.backend.entity.user.User;
import com.frietsync.backend.entity.user.Role;
import com.frietsync.backend.repository.user.UserRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

@Service
public class InviteServiceImpl implements InviteService {

    private static final Duration INVITE_VALIDITY = Duration.ofDays(7);

    private final InviteRepository inviteRepository;
    private final UserRepository userRepository;
    private final WorkspaceRepository workspaceRepository;
    private final ProjectRepository projectRepository;
    private final ProjectMemberRepository projectMemberRepository;
    private final EmailService emailService;

    public InviteServiceImpl(InviteRepository inviteRepository,
                             UserRepository userRepository,
                             WorkspaceRepository workspaceRepository,
                             ProjectRepository projectRepository,
                             ProjectMemberRepository projectMemberRepository,
                             EmailService emailService) {
        this.inviteRepository = inviteRepository;
        this.userRepository = userRepository;
        this.workspaceRepository = workspaceRepository;
        this.projectRepository = projectRepository;
        this.projectMemberRepository = projectMemberRepository;
        this.emailService = emailService;
    }

    @Override
    @Transactional
    public InviteResponse createInvite(InviteRequest request, UUID adminId) {
        return createInvite(request, adminId, false);
    }

    @Override
    @Transactional
    public InviteResponse createProjectInvite(
            UUID projectId,
            ProjectInviteRequest projectInviteRequest,
            UUID adminId
    ) {
        InviteRequest request = new InviteRequest();
        request.setEmail(projectInviteRequest.getEmail());
        request.setRole(projectInviteRequest.getRole());
        request.setPurpose(InvitePurpose.PROJECT_INVITE);
        request.setProjectId(projectId);
        request.setExpiresAt(projectInviteRequest.getExpiresAt());
        return createInvite(request, adminId, true);
    }

    private InviteResponse createInvite(InviteRequest request, UUID adminId, boolean allowProjectInvite) {
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

        String email = request.getEmail().trim().toLowerCase();

        User admin = userRepository.findById(adminId)
                .orElseThrow(() -> new BadRequestException("Admin not found"));

        if (admin.getRole() != Role.ADMIN) {
            throw new BadRequestException(
                    "Only admins can send invitations"
            );
        }

        if (email.equalsIgnoreCase(admin.getEmail())) {
            throw new BadRequestException("You cannot invite yourself");
        }

        if (admin.getWorkspaceId() == null) {
            throw new BadRequestException("Create a workspace before inviting members");
        }

        Workspace workspace = workspaceRepository.findByIdForUpdate(admin.getWorkspaceId())
                .orElseThrow(() -> new BadRequestException("Workspace not found"));
        if (!adminId.equals(workspace.getAdminId())) {
            throw new BadRequestException("Only the workspace owner can send invitations");
        }

        InvitePurpose purpose = request.getPurpose();
        if (purpose == null) {
            throw new BadRequestException("Invite purpose is required");
        }

        UUID projectId = null;
        if (purpose == InvitePurpose.PROJECT_INVITE) {
            if (!allowProjectInvite) {
                throw new BadRequestException("Project invites must use the project member invite endpoint");
            }
            projectId = validateProjectInvite(request, admin, email, now);
        } else if (request.getProjectId() != null) {
            throw new BadRequestException("Project ID is only valid for project invites");
        } else if (purpose == InvitePurpose.WORKSPACE_INVITE) {
            if (request.getRole() == null || request.getRole() == Role.ADMIN) {
                throw new BadRequestException(
                        "Invite role must be PROJECT_MANAGER, TEAM_LEAD, CONTRIBUTOR, or REPORTER"
                );
            }
        } else if (purpose == InvitePurpose.OWNERSHIP_TRANSFER) {
            User invitee = userRepository.findByEmailIgnoreCase(email)
                    .orElseThrow(() -> new BadRequestException(
                            "The invitee must already have an account"
                    ));

            if (invitee.getId().equals(adminId)) {
                throw new BadRequestException(
                        "You cannot transfer ownership to yourself"
                );
            }

            if (!admin.getWorkspaceId().equals(invitee.getWorkspaceId())) {
                throw new BadRequestException(
                        "The invitee must already belong to this workspace"
                );
            }
        } else {
            throw new BadRequestException(
                    "Unsupported invite purpose"
            );
        }
        Invite invite = new Invite();
        invite.setEmail(email);
        invite.setRole(purpose == InvitePurpose.OWNERSHIP_TRANSFER
                ? Role.CONTRIBUTOR
                : request.getRole());
        invite.setPurpose(purpose);
        invite.setStatus(InviteStatus.PENDING);
        invite.setInvitedBy(admin.getId());
        invite.setExpiresAt(expiresAt);
        invite.setWorkspaceId(admin.getWorkspaceId());
        invite.setProjectId(projectId);

        inviteRepository.save(invite);

        String inviteType = switch (purpose) {
            case WORKSPACE_INVITE -> invite.getRole().name();
            case OWNERSHIP_TRANSFER -> "OWNERSHIP_TRANSFER";
            case PROJECT_INVITE -> "PROJECT_INVITE";
        };

        emailService.sendInviteMail(email, inviteType);

        return toResponse(invite);
    }

    private UUID validateProjectInvite(
            InviteRequest request,
            User admin,
            String email,
            Instant now
    ) {
        UUID projectId = request.getProjectId();
        if (projectId == null) {
            throw new BadRequestException("Project ID is required for a project invite");
        }
        Project project = projectRepository.findByIdForUpdate(projectId)
                .orElseThrow(() -> new BadRequestException("Project not found"));
        if (!admin.getId().equals(project.getAdminId())) {
            throw new ForbiddenException("Only the project admin can invite project members");
        }
        if (project.getWorkspaceId() == null
                || !project.getWorkspaceId().equals(admin.getWorkspaceId())
                || !project.getAdminId().equals(admin.getId())) {
            throw new BadRequestException("Project admin must be the workspace admin");
        }
        if (request.getRole() == null || request.getRole() == Role.ADMIN) {
            throw new BadRequestException("Unsupported project invite role");
        }
        User invitee = userRepository.findByEmailIgnoreCase(email)
                .orElseThrow(() -> new BadRequestException("Project invitee must already have an account"));
        if (!project.getWorkspaceId().equals(invitee.getWorkspaceId())) {
            throw new BadRequestException("Project invitee must belong to the project's workspace");
        }
        if (invitee.getRole() != request.getRole()) {
            throw new BadRequestException("Invite role must match the invitee's global role");
        }
        if (invitee.getId().equals(project.getAdminId())) {
            throw new BadRequestException("Workspace admin is already the project admin");
        }
        if (projectMemberRepository.existsByProjectIdAndUserId(projectId, invitee.getId())
                || invitee.getId().equals(project.getProjectManagerId())
                || invitee.getId().equals(project.getTeamLeadId())) {
            throw new BadRequestException("User is already a member of this project");
        }
        if (inviteRepository.existsByProjectIdAndEmailIgnoreCaseAndStatus(
                projectId, email, InviteStatus.PENDING)) {
            throw new BadRequestException("A pending invite already exists for this project member");
        }
        ensureProjectRoleAvailable(project, request.getRole(), invitee.getId());
        ensureNoPendingLeadershipInvite(projectId, request.getRole(), now);
        return projectId;
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

        userRepository.findById(i.getInvitedBy()).ifPresent(inviter -> {
            r.setInvitedByName(inviter.getName());
            r.setInvitedByEmail(inviter.getEmail());
        });
        r.setWorkspaceId(i.getWorkspaceId());
        r.setProjectId(i.getProjectId());
        r.setCreatedAt(i.getCreatedAt());
        r.setAcceptedAt(i.getAcceptedAt());
        r.setExpiresAt(getExpirationTime(i));
        r.setPurpose(i.getPurpose());
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
            throw new BadRequestException("This invite does not belong to your account");
        }
        if (invite.getStatus() != InviteStatus.PENDING) {
            throw new BadRequestException("This invite is no longer pending");
        }
        Instant now = Instant.now();
        if (isExpired(invite, now)) {
            throw new BadRequestException("This invite has expired");
        }
        if (invite.getPurpose() == null) {
            throw new BadRequestException("Invite purpose is missing");
        }

        switch (invite.getPurpose()) {
            case PROJECT_INVITE -> {
                if (invite.getProjectId() == null || invite.getRole() == null) {
                    throw new BadRequestException("Project invite details are missing");
                }
                Project project = projectRepository.findByIdForUpdate(invite.getProjectId())
                        .orElseThrow(() -> new BadRequestException("Project not found"));
                if (!project.getWorkspaceId().equals(user.getWorkspaceId())
                        || !project.getWorkspaceId().equals(invite.getWorkspaceId())) {
                    throw new BadRequestException("You must belong to the project's workspace");
                }
                Workspace workspace = workspaceRepository.findById(project.getWorkspaceId())
                        .orElseThrow(() -> new BadRequestException("Project workspace not found"));
                if (!workspace.getAdminId().equals(project.getAdminId())) {
                    throw new BadRequestException("Project admin must be the workspace admin");
                }
                if (user.getRole() != invite.getRole()) {
                    throw new BadRequestException("Your global role does not match this project invite");
                }
                if (invite.getRole() == Role.ADMIN || userId.equals(project.getAdminId())) {
                    throw new BadRequestException("Workspace admin is the only project admin");
                }
                if (projectMemberRepository.existsByProjectIdAndUserId(project.getId(), userId)
                        || userId.equals(project.getProjectManagerId())
                        || userId.equals(project.getTeamLeadId())) {
                    throw new BadRequestException("User is already a member of this project");
                }
                ensureProjectRoleAvailable(project, invite.getRole(), userId);

                if (invite.getRole() == Role.PROJECT_MANAGER) {
                    project.setProjectManagerId(userId);
                } else if (invite.getRole() == Role.TEAM_LEAD) {
                    project.setTeamLeadId(userId);
                } else {
                    ProjectMember member = new ProjectMember();
                    member.setProjectId(project.getId());
                    member.setUserId(userId);
                    member.setRole(invite.getRole());
                    projectMemberRepository.save(member);
                }

                Set<UUID> projectUsers = new HashSet<>();
                projectUsers.add(project.getAdminId());
                if (project.getProjectManagerId() != null) {
                    projectUsers.add(project.getProjectManagerId());
                }
                if (project.getTeamLeadId() != null) {
                    projectUsers.add(project.getTeamLeadId());
                }
                projectMemberRepository.findByProjectId(project.getId())
                        .forEach(member -> projectUsers.add(member.getUserId()));
                project.setTeamSize(projectUsers.size());
                projectRepository.save(project);
            }

            case WORKSPACE_INVITE -> {
                if (user.getWorkspaceId() != null) {
                    throw new BadRequestException(
                            "You already belong to a workspace. Leave it before joining another."
                    );
                }
                user.setWorkspaceId(invite.getWorkspaceId());
                user.setRole(invite.getRole());
                userRepository.save(user);
            }

            case OWNERSHIP_TRANSFER -> {
                if (user.getWorkspaceId() == null
                        || !user.getWorkspaceId().equals(invite.getWorkspaceId())) {
                    throw new BadRequestException(
                            "You must be a member of this workspace to accept ownership"
                    );
                }
                Workspace workspace = workspaceRepository
                        .findByIdForUpdate(invite.getWorkspaceId())
                        .orElseThrow(() -> new BadRequestException("Workspace not found"));
                if (workspace.getAdminId().equals(userId)) {
                    throw new BadRequestException(
                            "You are already the owner of this workspace"
                    );
                }
                User currentOwner = userRepository.findById(workspace.getAdminId())
                        .orElseThrow(() -> new BadRequestException("Current owner not found"));

                workspace.setAdminId(userId);
                workspaceRepository.save(workspace);
                projectRepository.updateAdminIdByWorkspaceId(invite.getWorkspaceId(), userId);
                currentOwner.setRole(Role.CONTRIBUTOR);
                userRepository.save(currentOwner);
                user.setRole(Role.ADMIN);
                userRepository.save(user);
            }
        }

        invite.setStatus(InviteStatus.ACCEPTED);
        invite.setAcceptedAt(now);
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
    public InviteResponse revokeInvite(UUID adminId, UUID inviteId) {
        if (inviteId == null) {
            throw new BadRequestException("inviteId is required");
        }

        Invite invite = inviteRepository.findByIdForUpdate(inviteId)
                .orElseThrow(() -> new BadRequestException("Invite not found"));

        if (!adminId.equals(invite.getInvitedBy())) {
            throw new ForbiddenException("Only the user who created this invite can revoke it");
        }

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

    private void ensureProjectRoleAvailable(Project project, Role role, UUID inviteeId) {
        if (role == Role.PROJECT_MANAGER
                && project.getProjectManagerId() != null
                && !project.getProjectManagerId().equals(inviteeId)) {
            throw new BadRequestException("This project already has a project manager");
        }
        if (role == Role.TEAM_LEAD
                && project.getTeamLeadId() != null
                && !project.getTeamLeadId().equals(inviteeId)) {
            throw new BadRequestException("This project already has a team lead");
        }
        if ((role == Role.PROJECT_MANAGER || role == Role.TEAM_LEAD)
                && projectMemberRepository.findByProjectId(project.getId()).stream()
                .anyMatch(member -> member.getRole() == role && !member.getUserId().equals(inviteeId))) {
            throw new BadRequestException("This project already has a member with that leadership role");
        }
    }

    private void ensureNoPendingLeadershipInvite(UUID projectId, Role role, Instant now) {
        if (role != Role.PROJECT_MANAGER && role != Role.TEAM_LEAD) {
            return;
        }
        boolean hasPendingInvite = false;
        for (Invite pendingInvite : inviteRepository.findByProjectIdAndRoleAndPurposeAndStatus(
                projectId, role, InvitePurpose.PROJECT_INVITE, InviteStatus.PENDING)) {
            if (isExpired(pendingInvite, now)) {
                pendingInvite.setStatus(InviteStatus.EXPIRED);
                inviteRepository.save(pendingInvite);
            } else {
                hasPendingInvite = true;
            }
        }
        if (hasPendingInvite) {
            throw new BadRequestException("A pending invite already exists for this project leadership role");
        }
    }

}