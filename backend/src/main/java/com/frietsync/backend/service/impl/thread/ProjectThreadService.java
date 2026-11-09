package com.frietsync.backend.service.impl.thread;

import com.frietsync.backend.dto.thread.CommentResponse;
import com.frietsync.backend.dto.thread.CreateCommentRequest;
import com.frietsync.backend.dto.thread.CreateThreadRequest;
import com.frietsync.backend.dto.thread.ThreadDetailResponse;
import com.frietsync.backend.entity.project.Project;
import com.frietsync.backend.entity.thread.ProjectThread;
import com.frietsync.backend.entity.thread.ThreadComment;
import com.frietsync.backend.entity.user.User;
import com.frietsync.backend.exception.BadRequestException;
import com.frietsync.backend.exception.ResourceNotFoundException;
import com.frietsync.backend.repository.thread.ProjectThreadRepository;
import com.frietsync.backend.repository.thread.ThreadCommentRepository;
import com.frietsync.backend.repository.user.UserRepository;
import com.frietsync.backend.service.impl.project.ProjectAccess;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class ProjectThreadService {

    private final ProjectAccess projectAccess;
    private final ProjectThreadRepository threadRepository;
    private final ThreadCommentRepository commentRepository;
    private final UserRepository userRepository;

    @Transactional(readOnly = true)
    public Page<ProjectThread> getThreads(UUID projectId, Pageable pageable, UUID userId) {
        Project project = projectAccess.project(projectId);
        projectAccess.requireMember(project, userId);
        return threadRepository.findByProjectIdAndDeletedAtIsNullOrderByCreatedAtDesc(projectId, pageable);
    }

    @Transactional
    public ProjectThread createThread(UUID projectId, CreateThreadRequest request, UUID userId) {
        Project project = projectAccess.project(projectId);
        projectAccess.requireMember(project, userId);
        projectAccess.requireActiveProject(project);

        ProjectThread thread = new ProjectThread();
        thread.setProjectId(projectId);
        thread.setAuthorId(userId);
        thread.setTitle(request.getTitle().trim());
        thread.setContent(request.getContent().trim());
        thread.setUpvotes(0);
        return threadRepository.save(thread);
    }

    @Transactional(readOnly = true)
    public ThreadDetailResponse getThreadDetail(UUID projectId, UUID threadId, UUID userId) {
        Project project = projectAccess.project(projectId);
        projectAccess.requireMember(project, userId);

        ProjectThread thread = threadRepository.findById(threadId)
                .orElseThrow(() -> new ResourceNotFoundException("Thread not found"));
        if (thread.getDeletedAt() != null || !thread.getProjectId().equals(projectId)) {
            throw new ResourceNotFoundException("Thread not found");
        }

        List<ThreadComment> comments = commentRepository.findByThreadIdAndDeletedAtIsNullOrderByCreatedAtAsc(threadId);

        List<UUID> userIds = comments.stream().map(ThreadComment::getAuthorId).collect(Collectors.toList());
        userIds.add(thread.getAuthorId());
        Map<UUID, String> userNames = userRepository.findAllById(userIds).stream()
                .collect(Collectors.toMap(User::getId, u -> u.getFirstName() + " " + u.getLastName()));

        ThreadDetailResponse response = new ThreadDetailResponse();
        response.setId(thread.getId());
        response.setProjectId(thread.getProjectId());
        response.setAuthorId(thread.getAuthorId());
        response.setAuthorName(userNames.getOrDefault(thread.getAuthorId(), "Unknown User"));
        response.setTitle(thread.getTitle());
        response.setContent(thread.getContent());
        response.setUpvotes(thread.getUpvotes());
        response.setCreatedAt(thread.getCreatedAt());
        response.setUpdatedAt(thread.getUpdatedAt());

        List<CommentResponse> commentResponses = comments.stream().map(c -> {
            CommentResponse cr = new CommentResponse();
            cr.setId(c.getId());
            cr.setThreadId(c.getThreadId());
            cr.setParentCommentId(c.getParentCommentId());
            cr.setAuthorId(c.getAuthorId());
            cr.setAuthorName(userNames.getOrDefault(c.getAuthorId(), "Unknown User"));
            cr.setContent(c.getContent());
            cr.setUpvotes(c.getUpvotes());
            cr.setCreatedAt(c.getCreatedAt());
            cr.setUpdatedAt(c.getUpdatedAt());
            return cr;
        }).collect(Collectors.toList());

        response.setComments(commentResponses);
        return response;
    }

    @Transactional
    public void deleteThread(UUID projectId, UUID threadId, UUID userId) {
        Project project = projectAccess.project(projectId);
        projectAccess.requireMember(project, userId);
        projectAccess.requireActiveProject(project);

        ProjectThread thread = threadRepository.findById(threadId)
                .orElseThrow(() -> new ResourceNotFoundException("Thread not found"));
        if (thread.getDeletedAt() != null || !thread.getProjectId().equals(projectId)) {
            throw new ResourceNotFoundException("Thread not found");
        }

        if (!thread.getAuthorId().equals(userId)) {
            projectAccess.require(project, userId, com.frietsync.backend.entity.user.Role.ADMIN, com.frietsync.backend.entity.user.Role.PROJECT_MANAGER);
        }

        thread.setDeletedAt(Instant.now());
        threadRepository.save(thread);
    }

    @Transactional
    public ThreadComment addComment(UUID projectId, UUID threadId, CreateCommentRequest request, UUID userId) {
        Project project = projectAccess.project(projectId);
        projectAccess.requireMember(project, userId);
        projectAccess.requireActiveProject(project);

        ProjectThread thread = threadRepository.findById(threadId)
                .orElseThrow(() -> new ResourceNotFoundException("Thread not found"));
        if (thread.getDeletedAt() != null || !thread.getProjectId().equals(projectId)) {
            throw new ResourceNotFoundException("Thread not found");
        }

        if (request.getParentCommentId() != null) {
            ThreadComment parent = commentRepository.findById(request.getParentCommentId())
                    .orElseThrow(() -> new ResourceNotFoundException("Parent comment not found"));
            if (parent.getDeletedAt() != null || !parent.getThreadId().equals(threadId)) {
                throw new BadRequestException("Invalid parent comment");
            }
        }

        ThreadComment comment = new ThreadComment();
        comment.setThreadId(threadId);
        comment.setParentCommentId(request.getParentCommentId());
        comment.setAuthorId(userId);
        comment.setContent(request.getContent().trim());
        comment.setUpvotes(0);
        return commentRepository.save(comment);
    }

    @Transactional
    public ProjectThread upvoteThread(UUID projectId, UUID threadId, UUID userId) {
        Project project = projectAccess.project(projectId);
        projectAccess.requireMember(project, userId);

        ProjectThread thread = threadRepository.findById(threadId)
                .orElseThrow(() -> new ResourceNotFoundException("Thread not found"));
        if (thread.getDeletedAt() != null || !thread.getProjectId().equals(projectId)) {
            throw new ResourceNotFoundException("Thread not found");
        }

        thread.setUpvotes(thread.getUpvotes() + 1);
        return threadRepository.save(thread);
    }
}
