package com.frietsync.backend.service.impl.community;

import com.frietsync.backend.dto.community.CommentRequest;
import com.frietsync.backend.dto.community.CommentResponse;
import com.frietsync.backend.dto.community.CreatePostRequest;
import com.frietsync.backend.dto.community.PostPageResponse;
import com.frietsync.backend.dto.community.PostResponse;
import com.frietsync.backend.dto.community.UpdatePostRequest;
import com.frietsync.backend.entity.community.CommunityComment;
import com.frietsync.backend.entity.community.CommunityPost;
import com.frietsync.backend.entity.user.Role;
import com.frietsync.backend.entity.user.User;
import com.frietsync.backend.entity.workspace.Workspace;
import com.frietsync.backend.exception.BadRequestException;
import com.frietsync.backend.exception.ForbiddenException;
import com.frietsync.backend.exception.ResourceNotFoundException;
import com.frietsync.backend.repository.community.CommunityCommentRepository;
import com.frietsync.backend.repository.community.CommunityPostRepository;
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
import java.util.List;
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

        return toPostResponse(postRepository.save(post));
    }

    @Override
    @Transactional(readOnly = true)
    public PostPageResponse getPosts(UUID workspaceId, UUID userId, int page, int size) {
        requireWorkspaceMember(userId, workspaceId);
        checkPaging(page, size);

        Page<CommunityPost> result = postRepository.findByWorkspaceId(
                workspaceId,
                PageRequest.of(page, size, Sort.by(
                        Sort.Order.desc("createdAt"),
                        Sort.Order.desc("id"))));

        List<PostResponse> items = new ArrayList<>();
        for (CommunityPost post : result.getContent()) {
            items.add(toPostResponse(post));
        }
        return new PostPageResponse(items, page, size, result.getTotalElements(), result.getTotalPages());
    }

    @Override
    @Transactional(readOnly = true)
    public List<PostResponse> getPinnedPosts(UUID workspaceId, UUID userId) {
        requireWorkspaceMember(userId, workspaceId);

        List<PostResponse> items = new ArrayList<>();
        for (CommunityPost post : postRepository
                .findByWorkspaceIdAndPinnedAtIsNotNullOrderByPinnedAtDesc(workspaceId)) {
            items.add(toPostResponse(post));
        }
        return items;
    }

    @Override
    @Transactional(readOnly = true)
    public PostResponse getPost(UUID workspaceId, UUID userId, UUID postId) {
        requireWorkspaceMember(userId, workspaceId);
        return toPostResponse(findPost(workspaceId, postId));
    }

    @Override
    @Transactional
    public PostResponse updatePost(UUID workspaceId, UUID userId, UUID postId, UpdatePostRequest request) {
        requireWorkspaceMember(userId, workspaceId);
        CommunityPost post = findPost(workspaceId, postId);
        if (!userId.equals(post.getAuthorId())) {
            throw new ForbiddenException("You can only edit your own posts");
        }

        post.setTitle(request.getTitle().trim());
        post.setContent(request.getContent().trim());
        post.setEditedAt(Instant.now());
        return toPostResponse(postRepository.save(post));
    }

    @Override
    @Transactional
    public void deletePost(UUID workspaceId, UUID userId, UUID postId) {
        User user = requireWorkspaceMember(userId, workspaceId);
        CommunityPost post = findPost(workspaceId, postId);
        if (!userId.equals(post.getAuthorId()) && user.getRole() != Role.ADMIN) {
            throw new ForbiddenException("You can't delete this post");
        }

        commentRepository.deleteByPostId(postId);
        postRepository.delete(post);
    }

    @Override
    @Transactional
    public PostResponse pinPost(UUID workspaceId, UUID userId, UUID postId) {
        User user = requireWorkspaceMember(userId, workspaceId);
        if (user.getRole() != Role.ADMIN) {
            throw new ForbiddenException("Only admins can pin posts");
        }

        workspaceRepository.findByIdForUpdate(workspaceId)
                .orElseThrow(() -> new ResourceNotFoundException("Workspace not found"));

        CommunityPost post = findPost(workspaceId, postId);
        if (post.getPinnedAt() == null) {
            if (postRepository.countByWorkspaceIdAndPinnedAtIsNotNull(workspaceId) >= MAX_PINNED_POSTS) {
                throw new BadRequestException("A workspace can have up to " + MAX_PINNED_POSTS
                        + " pinned posts. Unpin one first");
            }
            post.setPinnedAt(Instant.now());
            post.setPinnedBy(userId);
            post = postRepository.save(post);
        }
        return toPostResponse(post);
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
        return toPostResponse(post);
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

        return toCommentResponse(commentRepository.save(comment));
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
            items.add(toCommentResponse(comment));
        }
        return items;
    }

    @Override
    @Transactional
    public CommentResponse updateComment(UUID workspaceId, UUID userId, UUID postId, UUID commentId,
                                         CommentRequest request) {
        requireWorkspaceMember(userId, workspaceId);
        findPost(workspaceId, postId);
        CommunityComment comment = findComment(postId, commentId);
        if (!userId.equals(comment.getAuthorId())) {
            throw new ForbiddenException("You can only edit your own comments");
        }

        comment.setContent(request.getContent().trim());
        comment.setEditedAt(Instant.now());
        return toCommentResponse(commentRepository.save(comment));
    }

    @Override
    @Transactional
    public void deleteComment(UUID workspaceId, UUID userId, UUID postId, UUID commentId) {
        User user = requireWorkspaceMember(userId, workspaceId);
        findPost(workspaceId, postId);
        CommunityComment comment = findComment(postId, commentId);
        if (!userId.equals(comment.getAuthorId()) && user.getRole() != Role.ADMIN) {
            throw new ForbiddenException("You can't delete this comment");
        }

        commentRepository.delete(comment);
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
        if (!workspaceId.equals(post.getWorkspaceId())) {
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

    private PostResponse toPostResponse(CommunityPost post) {
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
                post.getCreatedAt(),
                post.getUpdatedAt()
        );
    }

    private CommentResponse toCommentResponse(CommunityComment comment) {
        return new CommentResponse(
                comment.getId(),
                comment.getPostId(),
                comment.getAuthorId(),
                comment.getAuthorName(),
                comment.getContent(),
                comment.getEditedAt(),
                comment.getCreatedAt()
        );
    }
}