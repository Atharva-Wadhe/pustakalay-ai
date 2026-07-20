package ai.pustakalay.backend.retrieval.service;

import ai.pustakalay.backend.retrieval.entity.RetrievalLog;
import ai.pustakalay.backend.retrieval.entity.RetrievedChunk;
import ai.pustakalay.backend.retrieval.repository.RetrievalLogRepository;
import ai.pustakalay.backend.retrieval.repository.RetrievedChunkRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Service
@Slf4j
@RequiredArgsConstructor
public class RetrievalService {

    private final VectorStore vectorStore;
    private final RetrievalLogRepository retrievalLogRepository;
    private final RetrievedChunkRepository retrievedChunkRepository;

    @Transactional
    public List<RetrievedContext> retrieveContext(String query, UUID documentId, String category, int topK) {
        long startTime = System.currentTimeMillis();
        log.info("Retrieving context for query: '{}', documentId: {}, category: {}, topK: {}", query, documentId,
                category, topK);

        // 1. Build SearchRequest with optional filters
        SearchRequest request = SearchRequest.query(query).withTopK(topK);

        if (documentId != null) {
            request = request.withFilterExpression("documentId == '" + documentId + "'");
        } else if (category != null && !category.trim().isEmpty()) {
            request = request.withFilterExpression("category == '" + category + "'");
        }

        // 2. Perform vector similarity search
        List<org.springframework.ai.document.Document> results = vectorStore.similaritySearch(request);
        long retrievalTime = System.currentTimeMillis() - startTime;
        log.info("Vector search returned {} results in {} ms", results.size(), retrievalTime);

        // 3. Create Retrieval Log
        RetrievalLog logEntry = RetrievalLog.builder()
                .query(query)
                .topK(topK)
                .retrievalMs((int) retrievalTime)
                .build();
        logEntry = retrievalLogRepository.save(logEntry);

        // 4. Map results and save retrieved chunks
        List<RetrievedContext> contexts = new ArrayList<>();
        for (int i = 0; i < results.size(); i++) {
            org.springframework.ai.document.Document doc = results.get(i);
            String chunkIdStr = (String) doc.getMetadata().get("chunkId");
            UUID chunkId = chunkIdStr != null ? UUID.fromString(chunkIdStr) : null;

            Double score = 1.0;
            if (doc.getMetadata().containsKey("distance")) {
                score = ((Number) doc.getMetadata().get("distance")).doubleValue();
            } else if (doc.getMetadata().containsKey("score")) {
                score = ((Number) doc.getMetadata().get("score")).doubleValue();
            }

            // Log retrieved chunk
            if (chunkId != null) {
                RetrievedChunk retrievedChunk = RetrievedChunk.builder()
                        .retrievalId(logEntry.getId())
                        .chunkId(chunkId)
                        .similarityScore(score)
                        .rank(i + 1)
                        .build();
                retrievedChunkRepository.save(retrievedChunk);
            }

            // Get chunk details for citation
            String chapter = (String) doc.getMetadata().getOrDefault("chapter", "Unknown");
            String section = (String) doc.getMetadata().getOrDefault("section", "Unknown");
            Object pageStartObj = doc.getMetadata().get("pageStart");
            Integer pageStart = pageStartObj instanceof Number ? ((Number) pageStartObj).intValue() : 0;
            Object pageEndObj = doc.getMetadata().get("pageEnd");
            Integer pageEnd = pageEndObj instanceof Number ? ((Number) pageEndObj).intValue() : 0;
            String title = (String) doc.getMetadata().getOrDefault("title", "Unknown");

            contexts.add(RetrievedContext.builder()
                    .retrievalLogId(logEntry.getId())
                    .chunkId(chunkId)
                    .text(doc.getContent())
                    .similarityScore(score)
                    .rank(i + 1)
                    .documentTitle(title)
                    .chapter(chapter)
                    .section(section)
                    .pageStart(pageStart)
                    .pageEnd(pageEnd)
                    .build());
        }

        return contexts;
    }

    @lombok.Value
    @lombok.Builder
    public static class RetrievedContext {
        UUID retrievalLogId;
        UUID chunkId;
        String text;
        Double similarityScore;
        int rank;
        String documentTitle;
        String chapter;
        String section;
        int pageStart;
        int pageEnd;
    }
}
