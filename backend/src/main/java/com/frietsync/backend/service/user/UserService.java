package com.frietsync.backend.service.user;

import com.frietsync.backend.dto.user.ProfileResponse;
import com.frietsync.backend.dto.user.ProfileUpdateRequest;
import org.springframework.web.multipart.MultipartFile;

import java.util.UUID;

public interface UserService {
    ProfileResponse getProfile(UUID userId);

    ProfileResponse updateProfile(UUID userId, ProfileUpdateRequest request);

    ProfileResponse uploadProfilePicture(UUID userId, MultipartFile file);

    ProfileResponse removeProfilePicture(UUID userId);

    void leaveWorkspace(UUID userId);

    void deleteAccount(UUID userId);
}
