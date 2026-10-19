
package com.frietsync.backend.service.impl.call;

import com.frietsync.backend.entity.call.CallSession;
import com.frietsync.backend.entity.project.Project;
import com.frietsync.backend.entity.user.Role;
import com.frietsync.backend.exception.BadRequestException;
import com.frietsync.backend.exception.ForbiddenException;
import com.frietsync.backend.exception.ResourceNotFoundException;
import com.frietsync.backend.repository.call.CallSessionRepository;
import com.frietsync.backend.repository.project.ProjectRepository;
import com.frietsync.backend.service.call.CallService;
import com.frietsync.backend.service.call.JanusService;
import com.frietsync.backend.service.impl.project.ProjectAccess;

import lombok.RequiredArgsConstructor;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
@RequiredArgsConstructor
public class CallServiceImpl implements CallService {

    private final CallSessionRepository callSessionRepository;
    private final JanusService janusService;
    private final ProjectAccess projectAccess;
    private final ProjectRepository projectRepository;

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

            return callSessionRepository.save(call);

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
        return activeCall(projectId, callId);
    }

    @Override
    @Transactional
    public void leaveCall(UUID projectId, UUID callId, UUID userId) {
        Project project = projectAccess.project(projectId);
        projectAccess.requireMember(project, userId);
        activeCall(projectId, callId);
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

        return callSessionRepository.save(call);
    }

    private CallSession activeCall(UUID projectId, UUID callId) {
        CallSession call = callSessionRepository.findByIdAndProjectId(callId, projectId)
                .orElseThrow(() -> new ResourceNotFoundException("Call not found"));
        if (call.getStatus() != CallSession.CallStatus.ACTIVE) {
            throw new BadRequestException("Call has already ended");
        }
        return call;
    }
}
