
package com.frietsync.backend.service.impl.call;

import com.frietsync.backend.entity.call.CallSession;
import com.frietsync.backend.entity.project.Project;
import com.frietsync.backend.entity.user.Role;
import com.frietsync.backend.entity.user.User;
import com.frietsync.backend.exception.BadRequestException;
import com.frietsync.backend.exception.ForbiddenException;
import com.frietsync.backend.exception.ResourceNotFoundException;
import com.frietsync.backend.repository.call.CallSessionRepository;
import com.frietsync.backend.repository.project.ProjectRepository;
import com.frietsync.backend.repository.user.UserRepository;
import com.frietsync.backend.service.call.CallEvent;
import com.frietsync.backend.service.call.CallService;
import com.frietsync.backend.service.call.JanusService;
import com.frietsync.backend.service.impl.project.ProjectAccess;

import lombok.RequiredArgsConstructor;

import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class CallServiceImpl implements CallService {

    private final CallSessionRepository callSessionRepository;
    private final JanusService janusService;
    private final ProjectAccess projectAccess;
    private final ProjectRepository projectRepository;
    private final UserRepository userRepository;
    private final ApplicationEventPublisher eventPublisher;

    @Override
    @Transactional
    public CallSession startCall(UUID projectId, UUID userId) {
        Project project = projectRepository.findByIdForUpdate(projectId)
                .orElseThrow(() -> new ResourceNotFoundException("Project not found"));
        projectAccess.requireMember(project, userId);
        if (callSessionRepository
                .findByProjectIdAndStatus(
                        projectId,
                        CallSession.CallStatus.ACTIVE
                )
                .isPresent()) {
            throw new BadRequestException(
                    "An active call already exists for this project"
            );
        }

        long roomId = janusService.createRoom();

        try {
            CallSession call = new CallSession();
            call.setProjectId(projectId);
            call.setInitiatedBy(userId);
            call.setRoomId(roomId);
            call.setStatus(CallSession.CallStatus.ACTIVE);

            CallSession savedCall = callSessionRepository.save(call);
            publishEvent(project, savedCall, CallEvent.Type.STARTED, userId);
            return savedCall;

        } catch (RuntimeException exception) {
            try {
                janusService.destroyRoom(roomId);
            } catch (RuntimeException cleanupException) {
                exception.addSuppressed(cleanupException);
            }

            throw exception;
        }
    }

    @Override
    @Transactional
    public CallSession getActiveCall(UUID projectId, UUID userId) {
        Project project = projectAccess.project(projectId);
        projectAccess.requireMember(project, userId);
        return callSessionRepository.findByProjectIdAndStatus(
                        projectId, CallSession.CallStatus.ACTIVE)
                .orElseThrow(() -> new ResourceNotFoundException("No active call for this project"));
    }

    @Override
    @Transactional
    public CallSession joinCall(UUID projectId, UUID callId, UUID userId) {
        Project project = projectAccess.project(projectId);
        projectAccess.requireMember(project, userId);
        CallSession call = activeCall(projectId, callId);
        publishEvent(project, call, CallEvent.Type.JOINED, userId);
        return call;
    }

    @Override
    @Transactional
    public void leaveCall(UUID projectId, UUID callId, UUID userId) {
        Project project = projectAccess.project(projectId);
        projectAccess.requireMember(project, userId);
        CallSession call = activeCall(projectId, callId);
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
        if (call.getStatus() == CallSession.CallStatus.ENDED) {
            return call;
        }

        janusService.destroyRoom(call.getRoomId());

        call.setStatus(CallSession.CallStatus.ENDED);
        call.setEndedAt(java.time.Instant.now());

        CallSession savedCall = callSessionRepository.save(call);
        publishEvent(project, savedCall, CallEvent.Type.ENDED, userId);
        return savedCall;
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
        eventPublisher.publishEvent(new CallEvent(
                call.getProjectId(),
                project.getName(),
                type,
                userId,
                actorName,
                new CallEvent.CallDetails(
                        call.getId(),
                        call.getRoomId(),
                        call.getInitiatedBy(),
                        startedByName,
                        call.getStatus(),
                        call.getStartedAt(),
                        call.getEndedAt()
                ),
                Instant.now()
        ));
    }

    private String userName(UUID userId) {
        return userRepository.findById(userId)
                .map(User::getName)
                .orElse("Unknown user");
    }
}
