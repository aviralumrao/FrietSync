package com.frietsync.backend.controller.thread;

import com.frietsync.backend.dto.thread.CreateCommentRequest;
import com.frietsync.backend.dto.thread.CreateThreadRequest;
import com.frietsync.backend.dto.thread.ThreadDetailResponse;
import com.frietsync.backend.entity.thread.ProjectThread;
import com.frietsync.backend.entity.thread.ThreadComment;
import com.frietsync.backend.service.impl.thread.ProjectThreadService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/projects/{projectId}/threads")
@RequiredArgsConstructor
public class ProjectThreadController {

    private final ProjectThreadService threadService;

    @GetMapping
    public Page<ProjectThread> getThreads(@PathVariable UUID projectId,
                                          @PageableDefault(size = 20) Pageable pageable,
                                          Authentication authentication) {
        return threadService.getThreads(projectId, pageable, currentUserId(authentication));
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ProjectThread createThread(@PathVariable UUID projectId,
                                        @Valid @RequestBody CreateThreadRequest request,
                                        Authentication authentication) {
        return threadService.createThread(projectId, request, currentUserId(authentication));
    }

    @GetMapping("/{threadId}")
    public ThreadDetailResponse getThreadDetail(@PathVariable UUID projectId,
                                                @PathVariable UUID threadId,
                                                Authentication authentication) {
        return threadService.getThreadDetail(projectId, threadId, currentUserId(authentication));
    }

    @DeleteMapping("/{threadId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteThread(@PathVariable UUID projectId,
                             @PathVariable UUID threadId,
                             Authentication authentication) {
        threadService.deleteThread(projectId, threadId, currentUserId(authentication));
    }

    @PostMapping("/{threadId}/comments")
    @ResponseStatus(HttpStatus.CREATED)
    public ThreadComment addComment(@PathVariable UUID projectId,
                                    @PathVariable UUID threadId,
                                    @Valid @RequestBody CreateCommentRequest request,
                                    Authentication authentication) {
        return threadService.addComment(projectId, threadId, request, currentUserId(authentication));
    }

    @PostMapping("/{threadId}/upvote")
    public ProjectThread upvoteThread(@PathVariable UUID projectId,
                                      @PathVariable UUID threadId,
                                      Authentication authentication) {
        return threadService.upvoteThread(projectId, threadId, currentUserId(authentication));
    }

    private UUID currentUserId(Authentication authentication) {
        if (authentication != null && authentication.getPrincipal() instanceof UUID userId) {
            return userId;
        }
        throw new IllegalArgumentException("User not authenticated");
    }
}
