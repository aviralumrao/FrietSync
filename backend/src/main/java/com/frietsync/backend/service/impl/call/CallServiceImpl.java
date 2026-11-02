package com.frietsync.backend.service.impl.call;

import com.frietsync.backend.entity.call.CallParticipant;
import com.frietsync.backend.entity.call.CallSession;
import com.frietsync.backend.entity.call.CallType;
import com.frietsync.backend.entity.project.Project;
import com.frietsync.backend.entity.user.Role;
import com.frietsync.backend.entity.user.User;
import com.frietsync.backend.exception.BadRequestException;
import com.frietsync.backend.exception.ForbiddenException;
import com.frietsync.backend.exception.ResourceNotFoundException;
import com.frietsync.backend.repository.call.CallParticipantRepository;
import com.frietsync.backend.repository.call.CallSessionRepository;
import com.frietsync.backend.repository.project.ProjectRepository;
import com.frietsync.backend.repository.user.UserRepository;
import com.frietsync.backend.service.call.CallEvent;
import com.frietsync.backend.service.call.CallService;
import com.frietsync.backend.service.call.JanusService;
import com.frietsync.backend.service.impl.project.ProjectAccess;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import org.springframework.context.ApplicationEventPublisher;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;

@Service
@Slf4j
@RequiredArgsConstructor
public class CallServiceImpl implements CallService {

    private final CallSessionRepository callSessionRepository;
    private final CallParticipantRepository callParticipantRepository;
    private final JanusService janusService;
    private final ProjectAccess projectAccess;
    private final ProjectRepository projectRepository;
    private final UserRepository userRepository;
    private final ApplicationEventPublisher eventPublisher;
    private final CallRoomCleanupService callRoomCleanupService;

    @Override
    @Transactional
    public CallSession startCall(UUID projectId, UUID userId, CallType type) {
        Project project = projectRepository.findByIdForUpdate(projectId)
                .orElseThrow(() -> new ResourceNotFoundException("Project not found"));
        projectAccess.requireActiveProject(project);
        projectAccess.requireMember(project, userId);
        if (callSessionRepository
                .findByProjectIdAndTypeAndStatus(
                        projectId,
                        type,
                        CallSession.CallStatus.ACTIVE
                )
                .isPresent()) {
            throw new BadRequestException(
                    "An active " + type.name().toLowerCase() + " call already exists for this project"
            );
        }

        long roomId = janusService.createRoom(type);
        cleanupRoomIfTransactionRollsBack(roomId, type);

        CallSession call = new CallSession();
        call.setProjectId(projectId);
        call.setInitiatedBy(userId);
        call.setRoomId(roomId);
        call.setType(type);
        call.setStatus(CallSession.CallStatus.ACTIVE);

        CallSession savedCall = callSessionRepository.save(call);

        upsertParticipant(savedCall.getId(), userId, true);

        publishEvent(project, savedCall, CallEvent.Type.STARTED, userId);
        return savedCall;
    }

    @Override
    @Transactional
    public CallSession getActiveCall(UUID projectId, UUID userId, CallType type) {
        Project project = projectAccess.project(projectId);
        projectAccess.requireMember(project, userId);
        CallSession call = callSessionRepository.findByProjectIdAndTypeAndStatus(
                        projectId, type, CallSession.CallStatus.ACTIVE)
                .orElseThrow(() -> new ResourceNotFoundException("No active " + type.name().toLowerCase() + " call for this project"));

        upsertParticipant(call.getId(), userId, true);
        return call;
    }

    @Override
    @Transactional
    public CallSession joinCall(UUID projectId, UUID callId, UUID userId) {
        Project project = projectAccess.project(projectId);
        projectAccess.requireActiveProject(project);
        projectAccess.requireMember(project, userId);
        CallSession call = activeCall(projectId, callId);

        upsertParticipant(call.getId(), userId, true);
        publishEvent(project, call, CallEvent.Type.JOINED, userId);
        return call;
    }

    @Override
    @Transactional
    public void leaveCall(UUID projectId, UUID callId, UUID userId) {
        Project project = projectAccess.project(projectId);
        projectAccess.requireMember(project, userId);
        CallSession call = activeCall(projectId, callId);

        callParticipantRepository.findByCallIdAndUserId(callId, userId).ifPresent(p -> {
            p.setConnected(false);
            callParticipantRepository.save(p);
        });

        publishEvent(project, call, CallEvent.Type.LEFT, userId);
    }


    @Override
    @Transactional
    public CallSession endCall(UUID projectId, UUID callId, UUID userId) {
        Project project = projectAccess.project(projectId);
        projectAccess.requireMember(project, userId);
        CallSession call = callSessionRepository.findByIdAndProjectId(callId, projectId)
                .orElseThrow(() -> new ResourceNotFoundException("Call not found"));
        Role role = projectAccess.roleOf(project, userId);
        boolean canEnd = userId.equals(call.getInitiatedBy()) || role == Role.ADMIN
                || role == Role.PROJECT_MANAGER || role == Role.TEAM_LEAD;
        if (!canEnd) {
            throw new ForbiddenException("Only the call initiator or project leads can end this call");
        }
        return terminateCallInternal(call, project, userId);
    }

    @Transactional
    public CallSession terminateCallInternal(CallSession call, Project project, UUID actorUserId) {
        if (call.getStatus() == CallSession.CallStatus.ENDED) {
            return call;
        }

        call.setStatus(CallSession.CallStatus.ENDED);
        call.setEndedAt(Instant.now());

        CallSession savedCall = callSessionRepository.save(call);

        for (CallParticipant p : callParticipantRepository.findByCallId(savedCall.getId())) {
            p.setConnected(false);
            callParticipantRepository.save(p);
        }

        publishEvent(project, savedCall, CallEvent.Type.ENDED, actorUserId);
        cleanupRoomAfterCommit(savedCall.getId(), savedCall.getRoomId(), savedCall.getType());
        return savedCall;
    }

    @Scheduled(fixedDelayString = "${app.call.timeout.check-delay-ms:30000}")
    @Transactional
    public void checkAndEndAbandonedCalls() {
        Instant threshold = Instant.now().minus(2, ChronoUnit.MINUTES);
        List<CallSession> activeCalls = callSessionRepository.findByStatus(CallSession.CallStatus.ACTIVE);

        for (CallSession call : activeCalls) {
            List<CallParticipant> participants = callParticipantRepository.findByCallId(call.getId());
            boolean hasConnected = participants.stream().anyMatch(CallParticipant::isConnected);
            boolean recentActivity = participants.stream().anyMatch(p -> p.getLastActiveAt().isAfter(threshold));

            if (!hasConnected && !recentActivity) {
                log.info("Auto-ending abandoned call ID {} for project ID {}", call.getId(), call.getProjectId());
                projectRepository.findById(call.getProjectId()).ifPresent(project -> {
                    terminateCallInternal(call, project, call.getInitiatedBy());
                });
            }
        }
    }

    private void upsertParticipant(UUID callId, UUID userId, boolean connected) {
        CallParticipant participant = callParticipantRepository.findByCallIdAndUserId(callId, userId)
                .orElseGet(() -> {
                    CallParticipant p = new CallParticipant();
                    p.setCallId(callId);
                    p.setUserId(userId);
                    return p;
                });
        participant.setConnected(connected);
        participant.setLastActiveAt(Instant.now());
        callParticipantRepository.save(participant);
    }

    private void cleanupRoomIfTransactionRollsBack(long roomId, CallType type) {
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCompletion(int status) {
                if (status != STATUS_COMMITTED) {
                    try {
                        janusService.destroyRoom(roomId, type);
                    } catch (RuntimeException exception) {
                        log.error("Could not clean up Janus room {} after call creation rollback",
                                roomId, exception);
                    }
                }
            }
        });
    }

    private void cleanupRoomAfterCommit(UUID callId, long roomId, CallType type) {
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                callRoomCleanupService.cleanupEndedCall(callId, roomId, type);
            }
        });
    }

    private CallSession activeCall(UUID projectId, UUID callId) {
        CallSession call = callSessionRepository.findByIdAndProjectId(callId, projectId)
                .orElseThrow(() -> new ResourceNotFoundException("Call not found"));
        if (call.getStatus() != CallSession.CallStatus.ACTIVE) {
            throw new BadRequestException("Call has already ended");
        }
        return call;
    }

    private void publishEvent(Project project, CallSession call, CallEvent.Type type, UUID userId) {
        String actorName = userName(userId);
        String startedByName = call.getInitiatedBy().equals(userId)
                ? actorName
                : userName(call.getInitiatedBy());
        List<com.frietsync.backend.dto.call.CallParticipantResponse> participants =
                callParticipantRepository.findByCallIdAndConnectedTrue(call.getId()).stream()
                        .map(p -> new com.frietsync.backend.dto.call.CallParticipantResponse(
                                p.getUserId(),
                                userName(p.getUserId()),
                                p.getJoinedAt(),
                                p.getLastActiveAt()
                        ))
                        .toList();
        eventPublisher.publishEvent(new CallEvent(
                call.getProjectId(),
                project.getName(),
                type,
                userId,
                actorName,
                new CallEvent.CallDetails(
                        call.getId(),
                        call.getRoomId(),
                        call.getType(),
                        call.getInitiatedBy(),
                        startedByName,
                        call.getStatus(),
                        call.getStartedAt(),
                        call.getEndedAt()
                ),
                participants,
                Instant.now()
        ));
    }

    private String userName(UUID userId) {
        return userRepository.findById(userId)
                .map(User::getName)
                .orElse("Unknown user");
    }
}