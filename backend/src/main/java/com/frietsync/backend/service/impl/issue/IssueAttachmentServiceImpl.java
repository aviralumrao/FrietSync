package com.frietsync.backend.service.impl.issue;

import com.frietsync.backend.dto.issue.AttachmentResponse;
import com.frietsync.backend.entity.issue.Issue;
import com.frietsync.backend.entity.issue.IssueAttachment;
import com.frietsync.backend.entity.issue.IssueHistoryAction;
import com.frietsync.backend.entity.project.Project;
import com.frietsync.backend.entity.user.Role;
import com.frietsync.backend.service.security.VirusScanService;
import com.frietsync.backend.exception.BadRequestException;
import com.frietsync.backend.exception.ForbiddenException;
import com.frietsync.backend.exception.ResourceNotFoundException;
import com.frietsync.backend.repository.issue.IssueAttachmentRepository;
import com.frietsync.backend.repository.user.UserRepository;
import com.frietsync.backend.service.impl.project.ProjectAccess;
import com.frietsync.backend.service.impl.storage.S3UploadValidator;
import com.frietsync.backend.service.issue.IssueAttachmentService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.InputStreamResource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.web.multipart.MultipartFile;
import software.amazon.awssdk.core.ResponseInputStream;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectResponse;
import software.amazon.awssdk.services.s3.model.NoSuchKeyException;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.model.S3Exception;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.UUID;

@Slf4j
@Service
@Transactional
@RequiredArgsConstructor
public class IssueAttachmentServiceImpl implements IssueAttachmentService {

    private static final long MAX_ATTACHMENT_SIZE = 25L * 1024 * 1024;
    private final IssueAttachmentRepository attachmentRepository;
    private final ProjectAccess access;
    private final IssueHistoryRecorder history;
    private final S3Client s3Client;
    private final S3ObjectCleanupService cleanupService;
    private final UserRepository userRepository;
    private final VirusScanService virusScanService;

    @Value("${app.storage.path:./uploads}")
    private String storagePath;

    @Value("${aws.s3.bucket:}")
    private String bucketName;

    @Override
    public AttachmentResponse add(UUID issueId, MultipartFile file, UUID userId) {
        Issue issue = access.issue(issueId);
        Project project = access.project(issue.getProjectId());
        access.requireActiveProject(project);
        access.requireMember(project, userId);
        if (file == null || file.isEmpty()) {
            throw new BadRequestException("File is empty");
        }
        if (file.getSize() > MAX_ATTACHMENT_SIZE) {
            throw new BadRequestException("Issue attachments cannot exceed 25 MB");
        }
        if (bucketName == null || bucketName.isBlank()) {
            throw new BadRequestException("S3 bucket is not configured");
        }

        virusScanService.scanFile(file);

        String fileName = sanitize(file.getOriginalFilename());
        String contentType = S3UploadValidator.validateIssueAttachment(file, fileName);
        String key = "issues/" + issue.getProjectId() + "/" + issueId + "/" + UUID.randomUUID();
        try {
            s3Client.putObject(PutObjectRequest.builder()
                            .bucket(bucketName)
                            .key(key)
                            .contentType(contentType)
                            .build(),
                    RequestBody.fromBytes(file.getBytes()));
        } catch (IOException e) {
            log.error("Could not read issue attachment for S3 upload", e);
            throw new IllegalStateException("Could not read the uploaded file");
        }
        deleteS3ObjectOnRollback(key);

        IssueAttachment attachment = new IssueAttachment();
        attachment.setIssueId(issueId);
        attachment.setUploadedBy(userId);
        attachment.setFileName(fileName);
        attachment.setContentType(contentType);
        attachment.setSizeBytes(file.getSize());
        attachment.setStorageKey(key);
        attachmentRepository.save(attachment);

        history.record(issueId, userId, IssueHistoryAction.ATTACHMENT_ADDED,
                "attachment", null, attachment.getFileName());
        return toResponse(attachment);
    }

    @Override
    @Transactional(readOnly = true)
    public List<AttachmentResponse> list(UUID issueId, UUID userId) {
        Issue issue = access.issue(issueId);
        access.requireMember(access.project(issue.getProjectId()), userId);
        return attachmentRepository.findByIssueIdOrderByCreatedAtDesc(issueId).stream()
                .map(this::toResponse)
                .toList();
    }

    @Override
    public void delete(UUID attachmentId, UUID userId) {
        IssueAttachment attachment = find(attachmentId);
        Issue issue = access.issue(attachment.getIssueId());
        Project project = access.project(issue.getProjectId());
        Role role = access.requireMember(project, userId);

        boolean allowed = userId.equals(attachment.getUploadedBy())
                || role == Role.ADMIN
                || role == Role.PROJECT_MANAGER;
        if (!allowed) {
            throw new ForbiddenException("Only the uploader or project manager can remove this attachment");
        }

        deleteFile(attachment.getStorageKey());
        attachmentRepository.delete(attachment);
        history.record(issue.getId(), userId, IssueHistoryAction.ATTACHMENT_REMOVED,
                "attachment", attachment.getFileName(), null);
    }

    @Override
    @Transactional(readOnly = true)
    public AttachmentFile download(UUID attachmentId, UUID userId) {
        IssueAttachment attachment = find(attachmentId);
        Issue issue = access.issue(attachment.getIssueId());
        access.requireMember(access.project(issue.getProjectId()), userId);

        if (isS3Key(attachment.getStorageKey())) {
            try {
                ResponseInputStream<GetObjectResponse> object = s3Client.getObject(GetObjectRequest.builder()
                        .bucket(bucketName)
                        .key(attachment.getStorageKey())
                        .build());
                return new AttachmentFile(attachment.getFileName(), attachment.getContentType(),
                        new InputStreamResource(object));
            } catch (NoSuchKeyException exception) {
                throw new ResourceNotFoundException("File is no longer available");
            } catch (S3Exception exception) {
                if (exception.statusCode() == 404) {
                    throw new ResourceNotFoundException("File is no longer available");
                }
                throw exception;
            }
        }

        Path path = resolve(attachment.getStorageKey());
        if (!Files.exists(path)) {
            throw new ResourceNotFoundException("File is no longer available");
        }
        return new AttachmentFile(attachment.getFileName(), attachment.getContentType(),
                new FileSystemResource(path));
    }

    @Override
    public void deleteAllForIssue(UUID issueId) {
        List<IssueAttachment> all = attachmentRepository.findByIssueIdOrderByCreatedAtDesc(issueId);
        all.forEach(a -> deleteFile(a.getStorageKey()));
        attachmentRepository.deleteAll(all);
    }

    private IssueAttachment find(UUID attachmentId) {
        return attachmentRepository.findById(attachmentId)
                .orElseThrow(() -> new ResourceNotFoundException("Attachment not found"));
    }

    private Path resolve(String key) {
        Path base = Path.of(storagePath).toAbsolutePath().normalize();
        Path path = base.resolve(key).normalize();
        if (!path.startsWith(base)) {
            throw new IllegalStateException("Invalid storage path");
        }
        return path;
    }

    private void deleteFile(String key) {
        if (isS3Key(key)) {
            cleanupService.enqueue(bucketName, key);
            return;
        }
        runAfterCommit(() -> deleteLocalFile(key));
    }

    private void deleteLocalFile(String key) {
        try {
            Files.deleteIfExists(resolve(key));
        } catch (IOException e) {
            log.warn("Could not delete attachment file {}", key, e);
        }
    }

    private boolean isS3Key(String key) {
        return key != null && key.startsWith("issues/");
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

    private void runAfterCommit(Runnable action) {
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            action.run();
            return;
        }
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                action.run();
            }
        });
    }

    private String sanitize(String original) {
        String name = original == null ? "" : original.replaceAll("[\\\\/\\r\\n\"]", "_").trim();
        if (name.isEmpty()) {
            return "file";
        }
        return name.length() > 255 ? name.substring(0, 255) : name;
    }

    private AttachmentResponse toResponse(IssueAttachment a) {
        return AttachmentResponse.builder()
                .id(a.getId())
                .issueId(a.getIssueId())
                .fileName(a.getFileName())
                .contentType(a.getContentType())
                .sizeBytes(a.getSizeBytes())
                .uploadedBy(a.getUploadedBy())
                .uploadedByName(userRepository.findById(a.getUploadedBy()).map(user -> user.getName()).orElse(null))
                .createdAt(a.getCreatedAt())
                .build();
    }
}
