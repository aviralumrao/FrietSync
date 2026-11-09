package com.frietsync.backend.repository.thread;

import com.frietsync.backend.entity.thread.ThreadComment;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface ThreadCommentRepository extends JpaRepository<ThreadComment, UUID> {

    List<ThreadComment> findByThreadIdAndDeletedAtIsNullOrderByCreatedAtAsc(UUID threadId);
}
