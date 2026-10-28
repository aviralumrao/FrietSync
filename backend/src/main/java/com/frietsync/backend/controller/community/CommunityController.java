package com.frietsync.backend.controller.community;

import com.frietsync.backend.dto.community.CommentRequest;
import com.frietsync.backend.dto.community.CommentResponse;
import com.frietsync.backend.dto.community.CommunityEvent;
import com.frietsync.backend.dto.community.CreatePostRequest;
import com.frietsync.backend.dto.community.PostPageResponse;
import com.frietsync.backend.dto.community.PostResponse;
import com.frietsync.backend.dto.community.ReactionRequest;
import com.frietsync.backend.dto.community.UpdatePostRequest;
import com.frietsync.backend.service.community.CommunityService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/workspaces/{workspaceId}/community")
@RequiredArgsConstructor
public class CommunityController {

    private final CommunityService communityService;
    private final SimpMessagingTemplate messagingTemplate;

    @PostMapping("/posts")
    @ResponseStatus(HttpStatus.CREATED)
    public PostResponse createPost(
            @AuthenticationPrincipal UUID userId,
            @PathVariable UUID workspaceId,
            @Valid @RequestBody CreatePostRequest request
    ) {
        PostResponse post = communityService.createPost(workspaceId, userId, request);
        publish("POST_CREATED", workspaceId, post.id(), null, userId);
        return post;
    }

    @GetMapping("/posts")
    public PostPageResponse getPosts(
            @AuthenticationPrincipal UUID userId,
            @PathVariable UUID workspaceId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size
    ) {
        return communityService.getPosts(workspaceId, userId, page, size);
    }

    @GetMapping("/posts/pinned")
    public List<PostResponse> getPinnedPosts(
            @AuthenticationPrincipal UUID userId,
            @PathVariable UUID workspaceId
    ) {
        return communityService.getPinnedPosts(workspaceId, userId);
    }

    @GetMapping("/posts/{postId}")
    public PostResponse getPost(
            @AuthenticationPrincipal UUID userId,
            @PathVariable UUID workspaceId,
            @PathVariable UUID postId
    ) {
        return communityService.getPost(workspaceId, userId, postId);
    }

    @PatchMapping("/posts/{postId}")
    public PostResponse updatePost(
            @AuthenticationPrincipal UUID userId,
            @PathVariable UUID workspaceId,
            @PathVariable UUID postId,
            @Valid @RequestBody UpdatePostRequest request
    ) {
        PostResponse post = communityService.updatePost(workspaceId, userId, postId, request);
        publish("POST_UPDATED", workspaceId, postId, null, userId);
        return post;
    }

    @DeleteMapping("/posts/{postId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deletePost(
            @AuthenticationPrincipal UUID userId,
            @PathVariable UUID workspaceId,
            @PathVariable UUID postId
    ) {
        communityService.deletePost(workspaceId, userId, postId);
        publish("POST_DELETED", workspaceId, postId, null, userId);
    }

    @PostMapping("/posts/{postId}/pin")
    public PostResponse pinPost(
            @AuthenticationPrincipal UUID userId,
            @PathVariable UUID workspaceId,
            @PathVariable UUID postId
    ) {
        PostResponse post = communityService.pinPost(workspaceId, userId, postId);
        publish("POST_PINNED", workspaceId, postId, null, userId);
        return post;
    }

    @DeleteMapping("/posts/{postId}/pin")
    public PostResponse unpinPost(
            @AuthenticationPrincipal UUID userId,
            @PathVariable UUID workspaceId,
            @PathVariable UUID postId
    ) {
        PostResponse post = communityService.unpinPost(workspaceId, userId, postId);
        publish("POST_UNPINNED", workspaceId, postId, null, userId);
        return post;
    }

    @PutMapping("/posts/{postId}/reaction")
    public PostResponse reactToPost(
            @AuthenticationPrincipal UUID userId,
            @PathVariable UUID workspaceId,
            @PathVariable UUID postId,
            @Valid @RequestBody ReactionRequest request
    ) {
        PostResponse post = communityService.reactToPost(workspaceId, userId, postId, request);
        publish("POST_REACTION_CHANGED", workspaceId, postId, null, userId);
        return post;
    }

    @DeleteMapping("/posts/{postId}/reaction")
    public PostResponse removePostReaction(
            @AuthenticationPrincipal UUID userId,
            @PathVariable UUID workspaceId,
            @PathVariable UUID postId
    ) {
        PostResponse post = communityService.removePostReaction(workspaceId, userId, postId);
        publish("POST_REACTION_CHANGED", workspaceId, postId, null, userId);
        return post;
    }

    @PostMapping("/posts/{postId}/comments")
    @ResponseStatus(HttpStatus.CREATED)
    public CommentResponse addComment(
            @AuthenticationPrincipal UUID userId,
            @PathVariable UUID workspaceId,
            @PathVariable UUID postId,
            @Valid @RequestBody CommentRequest request
    ) {
        CommentResponse comment = communityService.addComment(workspaceId, userId, postId, request);
        publish("COMMENT_ADDED", workspaceId, postId, comment.id(), userId);
        return comment;
    }

    @GetMapping("/posts/{postId}/comments")
    public List<CommentResponse> getComments(
            @AuthenticationPrincipal UUID userId,
            @PathVariable UUID workspaceId,
            @PathVariable UUID postId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size
    ) {
        return communityService.getComments(workspaceId, userId, postId, page, size);
    }

    @PatchMapping("/posts/{postId}/comments/{commentId}")
    public CommentResponse updateComment(
            @AuthenticationPrincipal UUID userId,
            @PathVariable UUID workspaceId,
            @PathVariable UUID postId,
            @PathVariable UUID commentId,
            @Valid @RequestBody CommentRequest request
    ) {
        CommentResponse comment = communityService.updateComment(workspaceId, userId, postId, commentId, request);
        publish("COMMENT_UPDATED", workspaceId, postId, commentId, userId);
        return comment;
    }

    @DeleteMapping("/posts/{postId}/comments/{commentId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteComment(
            @AuthenticationPrincipal UUID userId,
            @PathVariable UUID workspaceId,
            @PathVariable UUID postId,
            @PathVariable UUID commentId
    ) {
        communityService.deleteComment(workspaceId, userId, postId, commentId);
        publish("COMMENT_DELETED", workspaceId, postId, commentId, userId);
    }

    @PutMapping("/posts/{postId}/comments/{commentId}/reaction")
    public CommentResponse reactToComment(
            @AuthenticationPrincipal UUID userId,
            @PathVariable UUID workspaceId,
            @PathVariable UUID postId,
            @PathVariable UUID commentId,
            @Valid @RequestBody ReactionRequest request
    ) {
        CommentResponse comment = communityService.reactToComment(workspaceId, userId, postId, commentId, request);
        publish("COMMENT_REACTION_CHANGED", workspaceId, postId, commentId, userId);
        return comment;
    }

    @DeleteMapping("/posts/{postId}/comments/{commentId}/reaction")
    public CommentResponse removeCommentReaction(
            @AuthenticationPrincipal UUID userId,
            @PathVariable UUID workspaceId,
            @PathVariable UUID postId,
            @PathVariable UUID commentId
    ) {
        CommentResponse comment = communityService.removeCommentReaction(workspaceId, userId, postId, commentId);
        publish("COMMENT_REACTION_CHANGED", workspaceId, postId, commentId, userId);
        return comment;
    }

    private void publish(String type, UUID workspaceId, UUID postId, UUID commentId, UUID actorId) {
        CommunityEvent event = new CommunityEvent(type, workspaceId, postId, commentId, actorId);
        messagingTemplate.convertAndSend("/topic/workspaces/" + workspaceId + "/community", event);
    }
}