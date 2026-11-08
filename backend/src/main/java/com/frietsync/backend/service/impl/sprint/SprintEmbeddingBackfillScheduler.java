package com.frietsync.backend.service.impl.sprint;

import com.frietsync.backend.entity.sprint.Sprint;
import com.frietsync.backend.repository.sprint.SprintRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
@RequiredArgsConstructor
public class SprintEmbeddingBackfillScheduler {
    private static final int BATCH_SIZE = 10;
    private final SprintRepository sprintRepository;
    private final SprintEmbeddingService sprintEmbeddingService;

    @Scheduled(
            fixedDelayString = "${app.ai.sprint-embedding-backfill-delay-ms:60000}",
            initialDelayString = "${app.ai.sprint-embedding-backfill-initial-delay-ms:60000}")
    public void backfillMissingEmbeddings() {
        List<Sprint> sprints = sprintRepository.findSprintsWithoutEmbedding(BATCH_SIZE);
        for (Sprint sprint : sprints) sprintEmbeddingService.generateAndSaveIfMissing(sprint);
    }
}
