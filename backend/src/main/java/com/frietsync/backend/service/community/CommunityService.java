package com.frietsync.backend.service.community;

import com.frietsync.backend.dto.community.CommentRequest;
import com.frietsync.backend.dto.community.CommentResponse;
import com.frietsync.backend.dto.community.CreatePostRequest;
import com.frietsync.backend.dto.community.PostPageResponse;
import com.frietsync.backend.dto.community.PostResponse;
import com.frietsync.backend.dto.community.ReactionRequest;
import com.frietsync.backend.dto.community.UpdatePostRequest;

import java.util.List;
import java.util.UUID;

public interface CommunityService {

    void assertWorkspaceMember(UUID workspaceId, UUID userId);

    PostResponse createPost(UUID workspaceId, UUID userId, CreatePostRequest request);

    PostPageResponse getPosts(UUID workspaceId, UUID userId, int page, int size);

    List<PostResponse> getPinnedPosts(UUID workspaceId, UUID userId);

    PostResponse getPost(UUID workspaceId, UUID userId, UUID postId);

    PostResponse updatePost(UUID workspaceId, UUID userId, UUID postId, UpdatePostRequest request);

    void deletePost(UUID workspaceId, UUID userId, UUID postId);

    PostResponse pinPost(UUID workspaceId, UUID userId, UUID postId);

    PostResponse unpinPost(UUID workspaceId, UUID userId, UUID postId);

    CommentResponse addComment(UUID workspaceId, UUID userId, UUID postId, CommentRequest request);

    List<CommentResponse> getComments(UUID workspaceId, UUID userId, UUID postId, int page, int size);

    CommentResponse updateComment(UUID workspaceId, UUID userId, UUID postId, UUID commentId,
                                  CommentRequest request);

    void deleteComment(UUID workspaceId, UUID userId, UUID postId, UUID commentId);

    PostResponse reactToPost(UUID workspaceId, UUID userId, UUID postId, ReactionRequest request);

    PostResponse removePostReaction(UUID workspaceId, UUID userId, UUID postId);

    CommentResponse reactToComment(UUID workspaceId, UUID userId, UUID postId, UUID commentId,
                                   ReactionRequest request);

    CommentResponse removeCommentReaction(UUID workspaceId, UUID userId, UUID postId, UUID commentId);
}