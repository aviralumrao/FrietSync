package com.frietsync.backend.service.impl.document;

import com.frietsync.backend.entity.document.ProjectDocument;
import com.frietsync.backend.entity.document.ProjectDocumentChunk;
import com.frietsync.backend.exception.AiServiceException;
import com.frietsync.backend.repository.document.ProjectDocumentChunkRepository;
import com.frietsync.backend.repository.document.ProjectDocumentRepository;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;

@Service
@RequiredArgsConstructor
public class DocumentEmbeddingService {

    private static final Logger LOG = LoggerFactory.getLogger(DocumentEmbeddingService.class);
    private static final int CHUNK_SIZE = 1500;
    private static final int CHUNK_OVERLAP = 200;

    private final EmbeddingModel embeddingModel;
    private final ProjectDocumentChunkRepository chunkRepository;
    private final ProjectDocumentRepository documentRepository;

    @Transactional
    public void generateAndSaveChunks(ProjectDocument document) {
        String content = document.getContent();
        if (content == null || content.isBlank()) {
            chunkRepository.deleteByDocumentId(document.getId());
            document.setEmbedding(null);
            documentRepository.save(document);
            return;
        }

        List<ProjectDocumentChunk> chunks = new ArrayList<>();
        int start = 0;
        int chunkIndex = 0;
        while (start < content.length()) {
            int end = Math.min(start + CHUNK_SIZE, content.length());
            String chunkText = content.substring(start, end);
            String textToEmbed = "Title: " + document.getTitle() + "\n\nContent:\n" + chunkText;
            ProjectDocumentChunk chunk = new ProjectDocumentChunk();
            chunk.setDocumentId(document.getId());
            chunk.setProjectId(document.getProjectId());
            chunk.setChunkIndex(chunkIndex++);
            chunk.setContent(chunkText);
            chunk.setEmbedding(formatVector(embed(textToEmbed, document)));
            chunks.add(chunk);
            if (end == content.length()) {
                break;
            }
            start = end - CHUNK_OVERLAP;
        }

        chunkRepository.deleteByDocumentId(document.getId());
        if (!chunks.isEmpty()) {
            chunkRepository.saveAll(chunks);
        }
        document.setEmbedding(null);
        documentRepository.save(document);
    }

    private float[] embed(String text, ProjectDocument document) {
        try {
            return embeddingModel.embed(text);
        } catch (RuntimeException ex) {
            LOG.error("Embedding document {} failed", document.getId(), ex);
            throw new AiServiceException("The AI service is not available right now. Please try again later.");
        }
    }

    private String formatVector(float[] vector) {
        StringBuilder sb = new StringBuilder("[");
        for (int i = 0; i < vector.length; i++) {
            sb.append(vector[i]);
            if (i < vector.length - 1) {
                sb.append(",");
            }
        }
        sb.append("]");
        return sb.toString();
    }
}
