package com.frietsync.backend.service.impl.project;

import com.frietsync.backend.dto.project.AttachIssuesRequest;
import com.frietsync.backend.dto.project.ReleaseRequest;
import com.frietsync.backend.dto.project.ReleaseResponse;
import com.frietsync.backend.entity.project.Project;
import com.frietsync.backend.entity.issue.Issue;
import com.frietsync.backend.entity.project.ProjectMember;
import com.frietsync.backend.entity.project.Release;
import com.frietsync.backend.entity.project.ReleaseIssue;
import com.frietsync.backend.exception.ForbiddenException;
import com.frietsync.backend.exception.BadRequestException;
import com.frietsync.backend.exception.ResourceNotFoundException;
import com.frietsync.backend.repository.project.ProjectMemberRepository;
import com.frietsync.backend.repository.project.ProjectRepository;
import com.frietsync.backend.repository.issue.IssueRepository;
import com.frietsync.backend.repository.project.ReleaseIssueRepository;
import com.frietsync.backend.repository.project.ReleaseRepository;
import com.frietsync.backend.service.project.ReleaseService;
import com.frietsync.backend.service.impl.issue.S3ObjectCleanupService;
import com.frietsync.backend.service.impl.storage.S3UploadValidator;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.web.multipart.MultipartFile;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import software.amazon.awssdk.services.s3.presigner.model.GetObjectPresignRequest;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.time.Duration;

@Service
@RequiredArgsConstructor
public class ReleaseServiceImpl implements ReleaseService {
    private final ReleaseRepository releaseRepository;
    private final ReleaseIssueRepository releaseIssueRepository;
    private final ProjectRepository projectRepository;
    private final IssueRepository issueRepository;
    private final ProjectMemberRepository projectMemberRepository;
    private final S3Client s3Client;
    private final S3Presigner s3Presigner;
    private final S3ObjectCleanupService cleanupService;
    private final ProjectAccess projectAccess;

    @Value("${aws.s3.bucket:}")
    private String bucketName;

    @Override
    @Transactional
    public ReleaseResponse create(UUID projectId, UUID userId, ReleaseRequest request) {
        Project project = findProject(projectId);
        requireManager(project, userId);
        Release release = new Release();
        release.setProjectId(projectId);
        apply(release, request);
        return response(releaseRepository.save(release));
    }

    @Override
    @Transactional(readOnly = true)
    public List<ReleaseResponse> list(UUID projectId, UUID userId) {
        Project project = findProject(projectId);
        requireMember(project, userId);
        return releaseRepository.findByProjectIdOrderByReleaseDateAsc(projectId).stream()
                .map(release -> response(release)).toList();
    }

    @Override
    @Transactional
    public ReleaseResponse update(UUID releaseId, UUID userId, ReleaseRequest request) {
        Release release = findRelease(releaseId);
        requireManager(findProject(release.getProjectId()), userId);
        apply(release, request);
        return response(releaseRepository.save(release));
    }

    @Override
    @Transactional
    public void delete(UUID releaseId, UUID userId) {
        Release release = findRelease(releaseId);
        requireManager(findProject(release.getProjectId()), userId);
        releaseIssueRepository.deleteByReleaseId(releaseId);
        enqueueStoredFileDeletion(release.getReleaseFileKey());
        releaseRepository.delete(release);
    }

    @Override
    @Transactional
    public void deleteAllForProject(UUID projectId) {
        List<Release> releases = releaseRepository.findByProjectIdOrderByReleaseDateAsc(projectId);
        for (Release release : releases) {
            releaseIssueRepository.deleteByReleaseId(release.getId());
            enqueueStoredFileDeletion(release.getReleaseFileKey());
        }
        releaseRepository.deleteAll(releases);
    }

    @Override
    @Transactional
    public ReleaseResponse attachIssues(UUID releaseId, UUID userId, AttachIssuesRequest request) {
        Release release = findRelease(releaseId);
        Project project = findProject(release.getProjectId());
        requireManagerOrTeamLead(project, userId);
        for (UUID issueId : request.getIssueIds()) {
            Issue issue = issueRepository.findById(issueId)
                    .orElseThrow(() -> new ResourceNotFoundException("Issue not found: " + issueId));
            if (!release.getProjectId().equals(issue.getProjectId())) {
                throw new BadRequestException("Every issue must belong to the release's project");
            }
        }
        for (UUID issueId : request.getIssueIds()) {
            if (!releaseIssueRepository.existsByReleaseIdAndIssueId(releaseId, issueId)) {
                ReleaseIssue link = new ReleaseIssue();
                link.setReleaseId(releaseId);
                link.setIssueId(issueId);
                releaseIssueRepository.save(link);
            }
        }
        return response(release);
    }

    @Override
    @Transactional
    public ReleaseResponse uploadFile(UUID releaseId, UUID userId, MultipartFile file) {
        Release release = findRelease(releaseId);
        requireManager(findProject(release.getProjectId()), userId);
        if (file == null || file.isEmpty()) {
            throw new BadRequestException("Release file must not be empty");
        }
        if (file.getSize() > 100L * 1024 * 1024) {
            throw new BadRequestException("Release file cannot exceed 100 MB");
        }
        if (bucketName == null || bucketName.isBlank()) {
            throw new BadRequestException("S3 bucket is not configured");
        }

        String originalName = file.getOriginalFilename();
        String safeName = originalName == null ? "release-file" : originalName.replaceAll("[^A-Za-z0-9._-]", "_");
        if (safeName.length() > 255) {
            safeName = safeName.substring(safeName.length() - 255);
        }
        String contentType = S3UploadValidator.validateReleaseFile(file, safeName);
        String key = "releases/" + releaseId + "/" + UUID.randomUUID() + "-" + safeName;
        try (java.io.InputStream input = file.getInputStream()) {
            s3Client.putObject(PutObjectRequest.builder()
                            .bucket(bucketName)
                            .key(key)
                            .contentType(contentType)
                            .build(),
                    RequestBody.fromInputStream(input, file.getSize()));
        } catch (java.io.IOException exception) {
            throw new BadRequestException("Could not read the uploaded release file");
        }
        deleteS3ObjectOnRollback(key);
        String previousKey = release.getReleaseFileKey();
        release.setReleaseFileKey(key);
        enqueueStoredFileDeletion(previousKey);
        return response(releaseRepository.save(release));
    }

    private void apply(Release release, ReleaseRequest request) {
        release.setName(request.getName().trim());
        release.setDescription(request.getDescription());
        release.setReleaseDate(request.getReleaseDate());
        String releaseUrl = request.getReleaseUrl();
        release.setReleaseUrl(releaseUrl == null || releaseUrl.isBlank() ? null : releaseUrl.trim());
    }

    private ReleaseResponse response(Release release) {
        List<UUID> issueIds = releaseIssueRepository.findByReleaseId(release.getId()).stream()
                .map(issue -> issue.getIssueId()).toList();
        ReleaseResponse response = ReleaseResponse.from(release, issueIds);
        if (release.getReleaseFileKey() != null && bucketName != null && !bucketName.isBlank()) {
            GetObjectRequest getObjectRequest = GetObjectRequest.builder()
                    .bucket(bucketName)
                    .key(release.getReleaseFileKey())
                    .build();
            GetObjectPresignRequest presignRequest = GetObjectPresignRequest.builder()
                    .signatureDuration(Duration.ofHours(1))
                    .getObjectRequest(getObjectRequest)
                    .build();
            response.setReleaseFileUrl(s3Presigner.presignGetObject(presignRequest).url().toString());
        }
        return response;
    }

    private void enqueueStoredFileDeletion(String key) {
        if (key != null && !key.isBlank()) {
            if (bucketName == null || bucketName.isBlank()) {
                throw new IllegalStateException("S3 bucket is not configured for release file cleanup");
            }
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

    private Project findProject(UUID id) {
        Optional<Project> project = projectRepository.findById(id);
        if (project.isEmpty()) {
            throw new ResourceNotFoundException("Project not found");
        }
        return project.get();
    }

    private Release findRelease(UUID id) {
        Optional<Release> release = releaseRepository.findById(id);
        if (release.isEmpty()) {
            throw new ResourceNotFoundException("Release not found");
        }
        return release.get();
    }

    private void requireManager(Project project, UUID userId) {
        projectAccess.requireWorkspaceAndActive(project, userId);
        if (userId == null || !userId.equals(project.getProjectManagerId())) {
            throw new ForbiddenException("Only the project manager can manage releases");
        }
    }

    private void requireManagerOrTeamLead(Project project, UUID userId) {
        projectAccess.requireWorkspaceAndActive(project, userId);
        if (userId == null || !(userId.equals(project.getProjectManagerId())
                || userId.equals(project.getTeamLeadId()))) {
            throw new ForbiddenException("Only the project manager or team lead can attach issues");
        }
    }

    private void requireMember(Project project, UUID userId) {
        projectAccess.requireWorkspaceAndActive(project, userId);
        boolean leader = userId != null && (userId.equals(project.getCreatedBy())
                || userId.equals(project.getAdminId()) || userId.equals(project.getProjectManagerId())
                || userId.equals(project.getTeamLeadId()));
        ProjectMember member = null;
        if (userId != null) {
            Optional<ProjectMember> memberResult = projectMemberRepository
                    .findByProjectIdAndUserId(project.getId(), userId);
            if (memberResult.isPresent()) {
                member = memberResult.get();
            }
        }
        if (!leader && member == null) {
            throw new ForbiddenException("You don't have access to this project's releases");
        }
    }
}
