package com.frietsync.backend.service.impl.issue;

import com.frietsync.backend.entity.issue.Issue;
import com.frietsync.backend.exception.AiServiceException;
import com.frietsync.backend.repository.issue.IssueRepository;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.StringJoiner;

@Service
@RequiredArgsConstructor
public class IssueEmbeddingService {

    private static final Logger LOG = LoggerFactory.getLogger(IssueEmbeddingService.class);

    private final EmbeddingModel embeddingModel;
    private final IssueRepository issueRepository;

    @Transactional
    public void generateAndSaveEmbedding(Issue issue) {
        generateAndSaveEmbedding(issue, false);
    }

    @Transactional
    public void generateAndSaveEmbeddingIfMissing(Issue issue) {
        generateAndSaveEmbedding(issue, true);
    }

    private void generateAndSaveEmbedding(Issue issue, boolean onlyIfMissing) {
        StringJoiner text = new StringJoiner("\n");
        text.add("Title: " + issue.getTitle());
        if (issue.getDescription() != null && !issue.getDescription().isBlank()) {
            text.add("Description: " + issue.getDescription());
        }

        float[] vector;
        try {
            vector = embeddingModel.embed(text.toString());
        } catch (RuntimeException ex) {
            LOG.error("Embedding issue {} failed", issue.getId(), ex);
            throw new AiServiceException("The AI service is not available right now. Please try again later.");
        }
        String embedding = formatVector(vector);
        if (onlyIfMissing) {
            issueRepository.updateEmbeddingIfMissing(issue.getId(), embedding);
        } else {
            issueRepository.updateEmbedding(issue.getId(), embedding);
        }
    }

    private String formatVector(float[] vector) {
        StringBuilder result = new StringBuilder("[");
        for (int i = 0; i < vector.length; i++) {
            if (i > 0) {
                result.append(",");
            }
            result.append(vector[i]);
        }
        return result.append("]").toString();
    }
}
