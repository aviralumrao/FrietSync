package com.frietsync.backend.service.impl.user;

import com.frietsync.backend.entity.user.Role;
import com.frietsync.backend.entity.user.User;
import com.frietsync.backend.exception.BadRequestException;
import com.frietsync.backend.repository.user.UserRepository;
import com.frietsync.backend.service.user.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
@RequiredArgsConstructor
public class UserServiceImpl implements UserService {

    private final UserRepository userRepository;

    @Override
    @Transactional
    public void leaveWorkspace(UUID userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() ->
                        new BadRequestException("User not found"));

        if (user.getWorkspaceId() == null) {
            throw new BadRequestException(
                    "You are not a member of any workspace");
        }

        if (user.getRole() == Role.ADMIN) {
            throw new BadRequestException(
                    "Admin cannot leave the workspace");
        }

        user.setWorkspaceId(null);
        userRepository.save(user);
    }
}