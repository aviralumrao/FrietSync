package com.frietsync.backend.service.impl.user;

import com.frietsync.backend.entity.user.Role;
import com.frietsync.backend.entity.user.User;
import com.frietsync.backend.entity.workspace.Workspace;
import com.frietsync.backend.exception.BadRequestException;
import com.frietsync.backend.repository.user.UserRepository;
import com.frietsync.backend.repository.workspace.WorkspaceRepository;
import com.frietsync.backend.service.user.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
@RequiredArgsConstructor
public class UserServiceImpl implements UserService {

    private final UserRepository userRepository;
    private final WorkspaceRepository workspaceRepository;

    @Override
    @Transactional
    public void leaveWorkspace(UUID userId) {
        User currentUser = userRepository.findById(userId)
                .orElseThrow(() -> new BadRequestException("User not found"));

        if (currentUser.getWorkspaceId() == null) {
            throw new BadRequestException("User does not belong to a workspace");
        }

        UUID workspaceId = currentUser.getWorkspaceId();
        Workspace workspace = workspaceRepository.findByIdForUpdate(workspaceId)
                .orElseThrow(() -> new BadRequestException("Workspace not found"));

        if (workspace.getAdminId().equals(userId)) {
            throw new BadRequestException(
                    "Workspace owner must transfer ownership before leaving"
            );
        }

        User user = userRepository.findByIdForUpdate(userId)
                .orElseThrow(() -> new BadRequestException("User not found"));
        if (!workspaceId.equals(user.getWorkspaceId())) {
            throw new BadRequestException("Workspace membership changed; please retry");
        }

        user.setWorkspaceId(null);
        user.setRole(Role.ADMIN);
        userRepository.save(user);
    }
}