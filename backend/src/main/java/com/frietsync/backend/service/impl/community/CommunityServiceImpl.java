package com.frietsync.backend.service.impl.community;

import com.frietsync.backend.dto.community.CommentRequest;
import com.frietsync.backend.dto.community.CommentResponse;
import com.frietsync.backend.dto.community.CreatePostRequest;
import com.frietsync.backend.dto.community.PostPageResponse;
import com.frietsync.backend.dto.community.PostResponse;
import com.frietsync.backend.dto.community.ReactionRequest;
import com.frietsync.backend.dto.community.UpdatePostRequest;
import com.frietsync.backend.entity.community.CommunityComment;
import com.frietsync.backend.entity.community.CommunityPost;
import com.frietsync.backend.entity.community.CommunityReaction;
import com.frietsync.backend.entity.community.ReactionTargetType;
import com.frietsync.backend.entity.community.ReactionType;
import com.frietsync.backend.entity.user.Role;
import com.frietsync.backend.entity.user.User;
import com.frietsync.backend.entity.workspace.Workspace;
import com.frietsync.backend.exception.BadRequestException;
import com.frietsync.backend.exception.ForbiddenException;
import com.frietsync.backend.exception.ResourceNotFoundException;
import com.frietsync.backend.repository.community.CommunityCommentRepository;
import com.frietsync.backend.repository.community.CommunityPostRepository;
import com.frietsync.backend.repository.community.CommunityReactionRepository;
import com.frietsync.backend.repository.user.UserRepository;
import com.frietsync.backend.repository.workspace.WorkspaceRepository;
import com.frietsync.backend.service.community.CommunityService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class CommunityServiceImpl implements CommunityService {

    private static final int MAX_PINNED_POSTS = 5;
    private static final int MAX_PAGE_SIZE = 50;

    private final UserRepository userRepository;
    private final WorkspaceRepository workspaceRepository;
    private final CommunityPostRepository postRepository;
    private final CommunityCommentRepository commentRepository;
    private final CommunityReactionRepository reactionRepository;

    @Override
    @Transactional(readOnly = true)
    public void assertWorkspaceMember(UUID workspaceId, UUID userId) {
        requireWorkspaceMember(userId, workspaceId);
    }

    @Override
    @Transactional
    public PostResponse createPost(UUID workspaceId, UUID userId, CreatePostRequest request) {
        User user = requireWorkspaceMember(userId, workspaceId);

        CommunityPost post = new CommunityPost();
        post.setWorkspaceId(workspaceId);
        post.setAuthorId(userId);
        post.setAuthorName(user.getName());
        post.setTitle(request.getTitle().trim());
        post.setContent(request.getContent().trim());

        return toPostResponse(postRepository.save(post), userId);
    }

    @Override
    @Transactional(readOnly = true)
    public PostPageResponse getPosts(UUID workspaceId, UUID userId, int page, int size) {
        requireWorkspaceMember(userId, workspaceId);
        checkPaging(page, size);

        Page<CommunityPost> result = postRepository.findByWorkspaceIdAndDeletedFalse(
                workspaceId,
                PageRequest.of(page, size, Sort.by(
                        Sort.Order.desc("createdAt"),
                        Sort.Order.desc("id"))));

        List<PostResponse> items = new ArrayList<>();
        for (CommunityPost post : result.getContent()) {
            items.add(toPostResponse(post, userId));
        }
        return new PostPageResponse(items, page, size, result.getTotalElements(), result.getTotalPages());
    }

    @Override
    @Transactional(readOnly = true)
    public List<PostResponse> getPinnedPosts(UUID workspaceId, UUID userId) {
        requireWorkspaceMember(userId, workspaceId);

        List<PostResponse> items = new ArrayList<>();
        for (CommunityPost post : postRepository
                .findByWorkspaceIdAndPinnedAtIsNotNullAndDeletedFalseOrderByPinnedAtDesc(workspaceId)) {
            items.add(toPostResponse(post, userId));
        }
        return items;
    }

    @Override
    @Transactional(readOnly = true)
    public PostResponse getPost(UUID workspaceId, UUID userId, UUID postId) {
        requireWorkspaceMember(userId, workspaceId);
        return toPostResponse(findPost(workspaceId, postId), userId);
    }

    @Override
    @Transactional
    public PostResponse updatePost(UUID workspaceId, UUID userId, UUID postId, UpdatePostRequest request) {
        User user = requireWorkspaceMember(userId, workspaceId);
        CommunityPost post = findPost(workspaceId, postId);
        if (!userId.equals(post.getAuthorId()) && user.getRole() != Role.ADMIN) {
            throw new ForbiddenException("Only the author or an admin can edit this post");
        }

        post.setTitle(request.getTitle().trim());
        post.setContent(request.getContent().trim());
        post.setEditedAt(Instant.now());
        return toPostResponse(postRepository.save(post), userId);
    }

    @Override
    @Transactional
    public void deletePost(UUID workspaceId, UUID userId, UUID postId) {
        User user = requireWorkspaceMember(userId, workspaceId);
        CommunityPost post = findPost(workspaceId, postId);
        if (!userId.equals(post.getAuthorId()) && user.getRole() != Role.ADMIN) {
            throw new ForbiddenException("Only the author or an admin can delete this post");
        }

        post.setDeleted(true);
        post.setDeletedAt(Instant.now());
        post.setDeletedBy(userId);
        post.setPinnedAt(null);
        post.setPinnedBy(null);
        postRepository.save(post);
    }

    @Override
    @Transactional
    public PostResponse pinPost(UUID workspaceId, UUID userId, UUID postId) {
        User user = requireWorkspaceMember(userId, workspaceId);
        if (user.getRole() != Role.ADMIN) {
            throw new ForbiddenException("Only admins can pin posts");
        }

        // lock the workspace row so two admins cannot pin past the limit at the same time
        workspaceRepository.findByIdForUpdate(workspaceId)
                .orElseThrow(() -> new ResourceNotFoundException("Workspace not found"));

        CommunityPost post = findPost(workspaceId, postId);
        if (post.getPinnedAt() == null) {
            if (postRepository.countByWorkspaceIdAndPinnedAtIsNotNullAndDeletedFalse(workspaceId)
                    >= MAX_PINNED_POSTS) {
                throw new BadRequestException("A workspace can have up to " + MAX_PINNED_POSTS
                        + " pinned posts. Unpin one first");
            }
            post.setPinnedAt(Instant.now());
            post.setPinnedBy(userId);
            post = postRepository.save(post);
        }
        return toPostResponse(post, userId);
    }

    @Override
    @Transactional
    public PostResponse unpinPost(UUID workspaceId, UUID userId, UUID postId) {
        User user = requireWorkspaceMember(userId, workspaceId);
        if (user.getRole() != Role.ADMIN) {
            throw new ForbiddenException("Only admins can unpin posts");
        }

        CommunityPost post = findPost(workspaceId, postId);
        if (post.getPinnedAt() != null) {
            post.setPinnedAt(null);
            post.setPinnedBy(null);
            post = postRepository.save(post);
        }
        return toPostResponse(post, userId);
    }

    @Override
    @Transactional
    public CommentResponse addComment(UUID workspaceId, UUID userId, UUID postId, CommentRequest request) {
        User user = requireWorkspaceMember(userId, workspaceId);
        findPost(workspaceId, postId);

        CommunityComment comment = new CommunityComment();
        comment.setPostId(postId);
        comment.setWorkspaceId(workspaceId);
        comment.setAuthorId(userId);
        comment.setAuthorName(user.getName());
        comment.setContent(request.getContent().trim());

        return toCommentResponse(commentRepository.save(comment), userId);
    }

    @Override
    @Transactional(readOnly = true)
    public List<CommentResponse> getComments(UUID workspaceId, UUID userId, UUID postId, int page, int size) {
        requireWorkspaceMember(userId, workspaceId);
        findPost(workspaceId, postId);
        checkPaging(page, size);

        List<CommentResponse> items = new ArrayList<>();
        List<CommunityComment> comments = commentRepository.findByPostId(
                postId,
                PageRequest.of(page, size, Sort.by(
                        Sort.Order.asc("createdAt"),
                        Sort.Order.asc("id"))));
        for (CommunityComment comment : comments) {
            items.add(toCommentResponse(comment, userId));
        }
        return items;
    }

    @Override
    @Transactional
    public CommentResponse updateComment(UUID workspaceId, UUID userId, UUID postId, UUID commentId,
                                         CommentRequest request) {
        User user = requireWorkspaceMember(userId, workspaceId);
        findPost(workspaceId, postId);
        CommunityComment comment = findComment(postId, commentId);
        if (!userId.equals(comment.getAuthorId()) && user.getRole() != Role.ADMIN) {
            throw new ForbiddenException("Only the author or an admin can edit this comment");
        }

        comment.setContent(request.getContent().trim());
        comment.setEditedAt(Instant.now());
        return toCommentResponse(commentRepository.save(comment), userId);
    }

    @Override
    @Transactional
    public void deleteComment(UUID workspaceId, UUID userId, UUID postId, UUID commentId) {
        User user = requireWorkspaceMember(userId, workspaceId);
        findPost(workspaceId, postId);
        CommunityComment comment = findComment(postId, commentId);
        if (!userId.equals(comment.getAuthorId()) && user.getRole() != Role.ADMIN) {
            throw new ForbiddenException("Only the author or an admin can delete this comment");
        }

        reactionRepository.deleteByTarget(ReactionTargetType.COMMENT, commentId);
        commentRepository.delete(comment);
    }

    @Override
    @Transactional
    public PostResponse reactToPost(UUID workspaceId, UUID userId, UUID postId, ReactionRequest request) {
        requireWorkspaceMember(userId, workspaceId);
        CommunityPost post = findPost(workspaceId, postId);

        saveReaction(workspaceId, userId, ReactionTargetType.POST, postId, request.getType());
        return toPostResponse(post, userId);
    }

    @Override
    @Transactional
    public PostResponse removePostReaction(UUID workspaceId, UUID userId, UUID postId) {
        requireWorkspaceMember(userId, workspaceId);
        CommunityPost post = findPost(workspaceId, postId);

        removeReaction(ReactionTargetType.POST, postId, userId);
        return toPostResponse(post, userId);
    }

    @Override
    @Transactional
    public CommentResponse reactToComment(UUID workspaceId, UUID userId, UUID postId, UUID commentId,
                                          ReactionRequest request) {
        requireWorkspaceMember(userId, workspaceId);
        findPost(workspaceId, postId);
        CommunityComment comment = findComment(postId, commentId);

        saveReaction(workspaceId, userId, ReactionTargetType.COMMENT, commentId, request.getType());
        return toCommentResponse(comment, userId);
    }

    @Override
    @Transactional
    public CommentResponse removeCommentReaction(UUID workspaceId, UUID userId, UUID postId, UUID commentId) {
        requireWorkspaceMember(userId, workspaceId);
        findPost(workspaceId, postId);
        CommunityComment comment = findComment(postId, commentId);

        removeReaction(ReactionTargetType.COMMENT, commentId, userId);
        return toCommentResponse(comment, userId);
    }

    private void saveReaction(UUID workspaceId, UUID userId, ReactionTargetType targetType,
                              UUID targetId, ReactionType type) {
        CommunityReaction reaction = reactionRepository
                .findByTargetTypeAndTargetIdAndUserId(targetType, targetId, userId)
                .orElse(null);
        if (reaction == null) {
            reaction = new CommunityReaction();
            reaction.setWorkspaceId(workspaceId);
            reaction.setTargetType(targetType);
            reaction.setTargetId(targetId);
            reaction.setUserId(userId);
        }
        reaction.setType(type);
        reactionRepository.save(reaction);
    }

    private void removeReaction(ReactionTargetType targetType, UUID targetId, UUID userId) {
        CommunityReaction reaction = reactionRepository
                .findByTargetTypeAndTargetIdAndUserId(targetType, targetId, userId)
                .orElse(null);
        if (reaction != null) {
            reactionRepository.delete(reaction);
        }
    }

    private User requireWorkspaceMember(UUID userId, UUID workspaceId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));
        if (!workspaceId.equals(user.getWorkspaceId())) {
            throw new ForbiddenException("You do not belong to this workspace");
        }
        Workspace workspace = workspaceRepository.findById(workspaceId)
                .orElseThrow(() -> new ResourceNotFoundException("Workspace not found"));
        if (workspace.isPendingDeletion()) {
            throw new ResourceNotFoundException("Workspace not found");
        }
        return user;
    }

    private CommunityPost findPost(UUID workspaceId, UUID postId) {
        CommunityPost post = postRepository.findById(postId)
                .orElseThrow(() -> new ResourceNotFoundException("Post not found"));
        if (!workspaceId.equals(post.getWorkspaceId()) || post.isDeleted()) {
            throw new ResourceNotFoundException("Post not found");
        }
        return post;
    }

    private CommunityComment findComment(UUID postId, UUID commentId) {
        CommunityComment comment = commentRepository.findById(commentId)
                .orElseThrow(() -> new ResourceNotFoundException("Comment not found"));
        if (!postId.equals(comment.getPostId())) {
            throw new ResourceNotFoundException("Comment not found");
        }
        return comment;
    }

    private void checkPaging(int page, int size) {
        if (page < 0) {
            throw new BadRequestException("Page must be zero or greater");
        }
        if (size < 1 || size > MAX_PAGE_SIZE) {
            throw new BadRequestException("Size must be between 1 and " + MAX_PAGE_SIZE);
        }
    }

    private Map<String, Long> countReactions(List<CommunityReaction> reactions) {
        Map<String, Long> counts = new LinkedHashMap<>();
        for (CommunityReaction reaction : reactions) {
            String key = reaction.getType().name();
            Long current = counts.get(key);
            if (current == null) {
                counts.put(key, 1L);
            } else {
                counts.put(key, current + 1L);
            }
        }
        return counts;
    }

    private String findMyReaction(List<CommunityReaction> reactions, UUID viewerId) {
        for (CommunityReaction reaction : reactions) {
            if (viewerId.equals(reaction.getUserId())) {
                return reaction.getType().name();
            }
        }
        return null;
    }

    private PostResponse toPostResponse(CommunityPost post, UUID viewerId) {
        List<CommunityReaction> reactions = reactionRepository
                .findByTargetTypeAndTargetId(ReactionTargetType.POST, post.getId());
        return new PostResponse(
                post.getId(),
                post.getWorkspaceId(),
                post.getAuthorId(),
                post.getAuthorName(),
                post.getTitle(),
                post.getContent(),
                post.getPinnedAt() != null,
                post.getPinnedAt(),
                post.getEditedAt(),
                commentRepository.countByPostId(post.getId()),
                countReactions(reactions),
                findMyReaction(reactions, viewerId),
                post.getCreatedAt(),
                post.getUpdatedAt()
        );
    }

    private CommentResponse toCommentResponse(CommunityComment comment, UUID viewerId) {
        List<CommunityReaction> reactions = reactionRepository
                .findByTargetTypeAndTargetId(ReactionTargetType.COMMENT, comment.getId());
        return new CommentResponse(
                comment.getId(),
                comment.getPostId(),
                comment.getAuthorId(),
                comment.getAuthorName(),
                comment.getContent(),
                comment.getEditedAt(),
                countReactions(reactions),
                findMyReaction(reactions, viewerId),
                comment.getCreatedAt()
        );
    }
}