package com.frietsync.backend.service.impl.user;

import com.frietsync.backend.dto.user.ProfileResponse;
import com.frietsync.backend.dto.user.ProfileUpdateRequest;
import com.frietsync.backend.entity.user.Role;
import com.frietsync.backend.entity.user.User;
import com.frietsync.backend.entity.workspace.Workspace;
import com.frietsync.backend.exception.BadRequestException;
import com.frietsync.backend.exception.ResourceNotFoundException;
import com.frietsync.backend.exception.StorageServiceException;
import com.frietsync.backend.repository.user.UserRepository;
import com.frietsync.backend.repository.user.AccountCleanupRepository;
import com.frietsync.backend.repository.workspace.WorkspaceRepository;
import com.frietsync.backend.service.impl.issue.S3ObjectCleanupService;
import com.frietsync.backend.service.impl.storage.S3UploadValidator;
import com.frietsync.backend.service.user.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.web.multipart.MultipartFile;
import software.amazon.awssdk.core.exception.SdkClientException;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.model.S3Exception;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import software.amazon.awssdk.services.s3.presigner.model.GetObjectPresignRequest;

import java.io.IOException;
import java.net.URI;
import java.time.Duration;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class UserServiceImpl implements UserService {

    private final UserRepository userRepository;
    private final WorkspaceRepository workspaceRepository;
    private final S3Client s3Client;
    private final S3Presigner s3Presigner;
    private final S3ObjectCleanupService cleanupService;
    private final AccountCleanupRepository accountCleanupRepository;

    @Value("${aws.s3.bucket:}")
    private String bucketName;

    @Override
    @Transactional(readOnly = true)
    public ProfileResponse getProfile(UUID userId) {
        return toProfile(findUser(userId));
    }

    @Override
    @Transactional
    public ProfileResponse updateProfile(UUID userId, ProfileUpdateRequest request) {
        User user = findUser(userId);

        if (request.getName() != null) {
            String name = request.getName().trim();
            if (name.isEmpty()) {
                throw new BadRequestException("Name must not be blank");
            }
            user.setName(name);
        }
        if (request.getBio() != null) {
            user.setBio(blankToNull(request.getBio()));
        }
        if (request.getJobTitle() != null) {
            user.setJobTitle(blankToNull(request.getJobTitle()));
        }
        if (request.getGithubUrl() != null) {
            user.setGithubUrl(validateUrl(request.getGithubUrl(), "GitHub URL"));
        }
        if (request.getLinkedinUrl() != null) {
            user.setLinkedinUrl(validateUrl(request.getLinkedinUrl(), "LinkedIn URL"));
        }
        if (request.getPortfolioUrl() != null) {
            user.setPortfolioUrl(validateUrl(request.getPortfolioUrl(), "Portfolio URL"));
        }

        return toProfile(userRepository.save(user));
    }

    @Override
    @Transactional
    public ProfileResponse uploadProfilePicture(UUID userId, MultipartFile file) {
        User user = findUser(userId);
        if (file == null || file.isEmpty()) {
            throw new BadRequestException("Profile picture must not be empty");
        }
        if (bucketName == null || bucketName.isBlank()) {
            throw new StorageServiceException("Profile picture storage is not configured");
        }

        String originalName = file.getOriginalFilename();
        String safeName = originalName == null ? "profile-picture.png"
                : originalName.replaceAll("[^A-Za-z0-9._-]", "_");
        if (safeName.length() > 255) {
            safeName = safeName.substring(safeName.length() - 255);
        }
        String contentType = S3UploadValidator.validateProfilePicture(file, safeName);
        String extension = safeName.substring(safeName.lastIndexOf('.') + 1).toLowerCase();
        String key = "users/" + userId + "/profile/" + UUID.randomUUID() + "." + extension;

        try (var input = file.getInputStream()) {
            s3Client.putObject(PutObjectRequest.builder()
                            .bucket(bucketName)
                            .key(key)
                            .contentType(contentType)
                            .build(),
                    RequestBody.fromInputStream(input, file.getSize()));
        } catch (IOException | SdkClientException | S3Exception exception) {
            throw new StorageServiceException("Could not upload profile picture", exception);
        }

        deleteS3ObjectOnRollback(key);
        String previousKey = user.getProfilePictureKey();
        user.setProfilePictureKey(key);
        User savedUser = userRepository.save(user);
        enqueueStoredFileDeletion(previousKey);
        return toProfile(savedUser);
    }

    @Override
    @Transactional
    public ProfileResponse removeProfilePicture(UUID userId) {
        User user = findUser(userId);
        String previousKey = user.getProfilePictureKey();
        user.setProfilePictureKey(null);
        User savedUser = userRepository.save(user);
        enqueueStoredFileDeletion(previousKey);
        return toProfile(savedUser);
    }

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

    @Override
    @Transactional
    public void deleteAccount(UUID userId) {
        User user = userRepository.findByIdForUpdate(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));
        UUID workspaceId = user.getWorkspaceId();
        UUID replacementId = null;
        if (workspaceId != null) {
            Workspace workspace = workspaceRepository.findByIdForUpdate(workspaceId)
                    .orElseThrow(() -> new ResourceNotFoundException("Workspace not found"));
            if (workspace.getAdminId().equals(userId)) {
                throw new BadRequestException("Workspace admin must transfer ownership before deleting the account");
            } else {
                replacementId = workspace.getAdminId();
            }
        }

        if (replacementId == null) {
            replacementId = UUID.randomUUID();
        }

        UUID attributionId = replacementId;
        accountCleanupRepository.deleteProjectMemberships(userId);
        accountCleanupRepository.clearIssueAssigner(userId);
        accountCleanupRepository.deleteIssueAssignments(userId);
        accountCleanupRepository.deleteMeetingAttendance(userId);
        accountCleanupRepository.deleteIssueAssignmentRequests(userId);
        accountCleanupRepository.deleteSprintAssignmentRequests(userId);
        accountCleanupRepository.deletePollVotes(userId);
        accountCleanupRepository.deleteNotifications(userId);
        accountCleanupRepository.deleteNotificationPreferences(userId);
        accountCleanupRepository.deleteInvites(userId);
        accountCleanupRepository.replaceProjectUserReferences(userId, attributionId);
        accountCleanupRepository.clearSprintAssignment(userId);
        accountCleanupRepository.replaceMeetingOrganizer(userId, attributionId);
        accountCleanupRepository.replaceChatSender(userId, attributionId);
        accountCleanupRepository.replaceIssueCreator(userId, attributionId);
        accountCleanupRepository.replaceSprintCreator(userId, attributionId);
        accountCleanupRepository.replacePollCreator(userId, attributionId);
        accountCleanupRepository.replaceIssueLinkCreator(userId, attributionId);
        accountCleanupRepository.replaceAttachmentUploader(userId, attributionId);
        accountCleanupRepository.replaceIssueHistoryActor(userId, attributionId);
        accountCleanupRepository.clearIssueDecisionMaker(userId);
        accountCleanupRepository.clearSprintReviewer(userId);
        accountCleanupRepository.clearNotificationActor(userId);

        String profilePictureKey = user.getProfilePictureKey();
        userRepository.delete(user);
        enqueueStoredFileDeletion(profilePictureKey);
    }

    private User findUser(UUID userId) {
        return userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));
    }

    private ProfileResponse toProfile(User user) {
        String pictureUrl = null;
        if (user.getProfilePictureKey() != null && bucketName != null && !bucketName.isBlank()) {
            GetObjectRequest getRequest = GetObjectRequest.builder()
                    .bucket(bucketName)
                    .key(user.getProfilePictureKey())
                    .build();
            GetObjectPresignRequest presignRequest = GetObjectPresignRequest.builder()
                    .signatureDuration(Duration.ofHours(1))
                    .getObjectRequest(getRequest)
                    .build();
            pictureUrl = s3Presigner.presignGetObject(presignRequest).url().toString();
        }
        return new ProfileResponse(user.getId(), user.getWorkspaceId(), user.getName(), user.getEmail(),
                user.getBio(), user.getJobTitle(), user.getRole(), user.getGithubUrl(), user.getLinkedinUrl(),
                user.getPortfolioUrl(), pictureUrl);
    }

    private String validateUrl(String value, String fieldName) {
        String url = blankToNull(value);
        if (url == null) {
            return null;
        }
        try {
            URI uri = URI.create(url);
            String scheme = uri.getScheme();
            if (uri.getHost() == null || scheme == null
                    || !(scheme.equalsIgnoreCase("http") || scheme.equalsIgnoreCase("https"))) {
                throw new IllegalArgumentException();
            }
        } catch (IllegalArgumentException exception) {
            throw new BadRequestException(fieldName + " must be a valid HTTP or HTTPS URL");
        }
        return url;
    }

    private String blankToNull(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.trim();
    }

    private void enqueueStoredFileDeletion(String key) {
        if (key != null && !key.isBlank()) {
            cleanupService.enqueue(bucketName, key);
        }
    }

    private void deleteS3ObjectOnRollback(String key) {
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            return;
        }
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCompletion(int status) {
                if (status != STATUS_COMMITTED) {
                    cleanupService.enqueue(bucketName, key);
                }
            }
        });
    }
}
