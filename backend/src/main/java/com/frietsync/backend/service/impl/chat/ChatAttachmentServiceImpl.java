package com.frietsync.backend.service.impl.chat;

import com.frietsync.backend.dto.chat.ChatAttachmentResponse;
import com.frietsync.backend.dto.chat.ChatAttachmentPageResponse;
import com.frietsync.backend.entity.chat.ChatAttachment;
import com.frietsync.backend.service.security.VirusScanService;
import com.frietsync.backend.exception.BadRequestException;
import com.frietsync.backend.exception.ResourceNotFoundException;
import com.frietsync.backend.repository.chat.ChatAttachmentRepository;
import com.frietsync.backend.service.chat.ChatAttachmentService;
import com.frietsync.backend.service.chat.ChatAccessService;
import com.frietsync.backend.service.impl.issue.S3ObjectCleanupService;
import com.frietsync.backend.service.impl.storage.S3UploadValidator;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
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
import java.util.List;
import java.util.Locale;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional
public class ChatAttachmentServiceImpl implements ChatAttachmentService {

    private static final long MAX_SIZE = 25L * 1024 * 1024;
    private static final int ATTACHMENT_PAGE_SIZE = 10;

    private final ChatAttachmentRepository repository;
    private final ChatAccessService chatAccessService;
    private final S3Client s3Client;
    private final S3ObjectCleanupService cleanupService;
    private final VirusScanService virusScanService;

    @Value("${aws.s3.bucket:}")
    private String bucketName;

    @Override
    public ChatAttachmentResponse upload(UUID projectId, UUID userId, MultipartFile file) {
        chatAccessService.assertProjectAccess(projectId, userId);
        if (file == null || file.isEmpty()) {
            throw new BadRequestException("File is empty");
        }
        if (file.getSize() > MAX_SIZE) {
            throw new BadRequestException("Chat attachments cannot exceed 25 MB");
        }
        if (bucketName == null || bucketName.isBlank()) {
            throw new BadRequestException("S3 bucket is not configured");
        }

        virusScanService.scanFile(file);

        String fileName = sanitize(file.getOriginalFilename());
        S3UploadValidator.validateChatAttachment(file, fileName);
        String contentType = contentType(fileName);
        String key = "chat/" + projectId + "/" + UUID.randomUUID();
        try {
            s3Client.putObject(PutObjectRequest.builder().bucket(bucketName).key(key)
                            .contentType(contentType).build(),
                    RequestBody.fromBytes(file.getBytes()));
        } catch (IOException exception) {
            log.error("Could not read chat attachment for S3 upload", exception);
            throw new IllegalStateException("Could not read the uploaded file");
        }
        deleteS3ObjectOnRollback(key);

        ChatAttachment attachment = new ChatAttachment();
        attachment.setProjectId(projectId);
        attachment.setUploadedBy(userId);
        attachment.setFileName(fileName);
        attachment.setContentType(contentType);
        attachment.setSizeBytes(file.getSize());
        attachment.setStorageKey(key);
        return ChatAttachmentResponse.fromEntity(repository.save(attachment));
    }

    @Override
    public List<ChatAttachmentResponse> bindToMessage(
            UUID projectId, UUID userId, UUID messageId, List<UUID> ids) {
        if (ids == null || ids.isEmpty()) {
            return List.of();
        }
        if (ids.size() > 10 || ids.stream().distinct().count() != ids.size()) {
            throw new BadRequestException("A message can include up to 10 unique attachments");
        }
        List<ChatAttachment> attachments = repository.findByProjectIdAndIdInAndMessageIdIsNull(projectId, ids);
        if (attachments.size() != ids.size() || attachments.stream().anyMatch(a -> !userId.equals(a.getUploadedBy()))) {
            throw new BadRequestException("One or more attachments are unavailable");
        }
        attachments.forEach(attachment -> attachment.setMessageId(messageId));
        return repository.saveAll(attachments).stream().map(ChatAttachmentResponse::fromEntity).toList();
    }

    @Override
    public void deleteAllForMessage(UUID messageId) {
        List<ChatAttachment> attachments = repository.findByMessageIdOrderByCreatedAtAsc(messageId);
        attachments.stream()
                .map(ChatAttachment::getStorageKey)
                .filter(key -> key != null && !key.isBlank())
                .forEach(key -> cleanupService.enqueue(bucketName, key));
        repository.deleteAll(attachments);
    }

    @Override
    @Transactional(readOnly = true)
    public ChatAttachmentPageResponse listProjectAttachments(UUID projectId, UUID userId, int page) {
        chatAccessService.assertProjectAccess(projectId, userId);
        if (page < 0) {
            throw new BadRequestException("Page must be zero or greater");
        }
        Page<ChatAttachment> result = repository
                .findByProjectIdAndMessageIdIsNotNullOrderByCreatedAtDescIdDesc(
                        projectId, PageRequest.of(page, ATTACHMENT_PAGE_SIZE));
        return ChatAttachmentPageResponse.builder()
                .items(result.getContent().stream().map(ChatAttachmentResponse::fromEntity).toList())
                .page(result.getNumber())
                .size(result.getSize())
                .totalElements(result.getTotalElements())
                .totalPages(result.getTotalPages())
                .build();
    }

    @Override
    @Transactional(readOnly = true)
    public AttachmentFile download(UUID projectId, UUID attachmentId, UUID userId) {
        chatAccessService.assertProjectAccess(projectId, userId);
        ChatAttachment attachment = repository.findById(attachmentId)
                .filter(a -> projectId.equals(a.getProjectId()) && a.getMessageId() != null)
                .orElseThrow(() -> new ResourceNotFoundException("Attachment not found"));
        try {
            ResponseInputStream<GetObjectResponse> object = s3Client.getObject(GetObjectRequest.builder()
                    .bucket(bucketName).key(attachment.getStorageKey()).build());
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

    @Override
    public void deleteAllForProject(UUID projectId) {
        List<ChatAttachment> all = repository.findByProjectId(projectId);
        all.forEach(a -> {
            if (a.getStorageKey() != null && !a.getStorageKey().isBlank()) {
                cleanupService.enqueue(bucketName, a.getStorageKey());
            }
        });
        repository.deleteAll(all);
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

    private String sanitize(String original) {
        String name = original == null ? "" : original.replaceAll("[\\\\/\\r\\n\"]", "_").trim();
        if (name.isEmpty()) {
            return "file";
        }
        return name.length() > 255 ? name.substring(0, 255) : name;
    }

    private String contentType(String fileName) {
        String extension = fileName.substring(fileName.lastIndexOf('.') + 1).toLowerCase(Locale.ROOT);
        return switch (extension) {
            case "png" -> "image/png";
            case "jpg", "jpeg" -> "image/jpeg";
            case "gif" -> "image/gif";
            case "webp" -> "image/webp";
            case "pdf" -> "application/pdf";
            case "mp3" -> "audio/mpeg";
            case "wav" -> "audio/wav";
            case "m4a" -> "audio/mp4";
            case "ogg" -> "audio/ogg";
            case "webm" -> "audio/webm";
            default -> "application/octet-stream";
        };
    }
}
