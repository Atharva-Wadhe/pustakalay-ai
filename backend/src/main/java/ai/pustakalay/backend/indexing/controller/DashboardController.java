package ai.pustakalay.backend.indexing.controller;

import ai.pustakalay.backend.books.repository.DocumentRepository;
import ai.pustakalay.backend.indexing.entity.IndexingJob;
import ai.pustakalay.backend.indexing.repository.DocumentChunkRepository;
import ai.pustakalay.backend.indexing.repository.IndexingJobRepository;
import ai.pustakalay.backend.retrieval.entity.RetrievalLog;
import ai.pustakalay.backend.retrieval.repository.RetrievalLogRepository;
import lombok.Builder;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Sort;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
public class DashboardController {

    private final IndexingJobRepository indexingJobRepository;
    private final RetrievalLogRepository retrievalLogRepository;
    private final DocumentRepository documentRepository;
    private final DocumentChunkRepository documentChunkRepository;

    @GetMapping("/jobs")
    public ResponseEntity<List<IndexingJob>> getAllJobs() {
        return ResponseEntity.ok(indexingJobRepository.findAll(Sort.by(Sort.Direction.DESC, "queuedAt")));
    }

    @GetMapping("/jobs/{id}")
    public ResponseEntity<IndexingJob> getJobById(@PathVariable UUID id) {
        return indexingJobRepository.findById(id)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @GetMapping("/analytics/retrieval-logs")
    public ResponseEntity<List<RetrievalLog>> getRetrievalLogs() {
        return ResponseEntity.ok(retrievalLogRepository.findAll(Sort.by(Sort.Direction.DESC, "createdAt")));
    }

    @GetMapping("/analytics/summary")
    public ResponseEntity<LibrarySummary> getLibrarySummary() {
        long totalBooks = documentRepository.count();
        long totalChunks = documentChunkRepository.count();
        long totalQueries = retrievalLogRepository.count();
        long activeJobs = indexingJobRepository.findAll().stream()
                .filter(j -> j.getStatus() == IndexingJob.JobStatus.RUNNING
                        || j.getStatus() == IndexingJob.JobStatus.QUEUED)
                .count();

        return ResponseEntity.ok(LibrarySummary.builder()
                .totalBooks(totalBooks)
                .totalChunks(totalChunks)
                .totalQueries(totalQueries)
                .activeJobs(activeJobs)
                .build());
    }

    @Data
    @Builder
    public static class LibrarySummary {
        private long totalBooks;
        private long totalChunks;
        private long totalQueries;
        private long activeJobs;
    }
}
