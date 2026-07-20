package ai.pustakalay.backend.indexing.service;

import ai.pustakalay.backend.books.entity.Document;
import ai.pustakalay.backend.books.entity.DocumentEvent;
import ai.pustakalay.backend.books.repository.DocumentRepository;
import ai.pustakalay.backend.books.service.BookService;
import ai.pustakalay.backend.indexing.entity.IndexingJob;
import ai.pustakalay.backend.indexing.event.IndexingEvent;
import ai.pustakalay.backend.indexing.repository.DocumentChunkRepository;
import ai.pustakalay.backend.indexing.repository.IndexingJobRepository;
import ai.pustakalay.backend.qdrant.service.QdrantService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.event.EventListener;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;
import java.util.UUID;

@Service
@Slf4j
@RequiredArgsConstructor
public class IndexingOrchestrator {

    private final IndexingJobRepository indexingJobRepository;
    private final DocumentChunkRepository documentChunkRepository;
    private final DocumentRepository documentRepository;
    private final BookService bookService;
    private final QdrantService qdrantService;
    private final KafkaTemplate<String, IndexingEvent> kafkaTemplate;

    @Value("${app.kafka.topics.indexing-jobs}")
    private String indexingJobsTopic;

    @EventListener
    @Transactional
    public void handleBookRegistered(BookService.BookRegisteredEvent event) {
        log.info("Received BookRegisteredEvent for bookId: {}", event.getBookId());
        triggerIndexing(event.getBookId(), IndexingJob.TriggerType.AUTO);
    }

    @EventListener
    @Transactional
    public void handleBookDeleted(BookService.BookDeletedEvent event) {
        UUID bookId = event.getBookId();
        log.info("Received BookDeletedEvent for bookId: {}", bookId);

        // 1. Delete from Qdrant
        qdrantService.deletePointsByDocumentId(bookId);

        // 2. Delete chunks from PostgreSQL
        documentChunkRepository.deleteByDocumentId(bookId);

        log.info("Cleaned up database chunks and Qdrant points for bookId: {}", bookId);
    }

    @Transactional
    public IndexingJob triggerIndexing(UUID bookId, IndexingJob.TriggerType triggerType) {
        Document document = documentRepository.findById(bookId)
                .orElseThrow(() -> new IllegalArgumentException("Document not found: " + bookId));

        // Update document status
        document.setStatus(Document.DocumentStatus.INDEXING);
        document.setMetadataStatus(Document.DocumentStatus.INDEXING);
        documentRepository.save(document);

        // Create Indexing Job
        IndexingJob job = IndexingJob.builder()
                .documentId(bookId)
                .triggerType(triggerType)
                .status(IndexingJob.JobStatus.QUEUED)
                .progress(0)
                .embeddingModel("nomic-embed-text")
                .chunkStrategy("Semantic")
                .chunkSize(600)
                .chunkOverlap(100)
                .indexVersion(1)
                .build();

        job = indexingJobRepository.save(job);

        bookService.logEvent(bookId, DocumentEvent.EventType.INDEX_REQUESTED,
                Map.of("jobId", job.getId(), "triggerType", triggerType.name()));

        // Publish event to Kafka
        IndexingEvent kafkaEvent = new IndexingEvent(bookId, job.getId());
        kafkaTemplate.send(indexingJobsTopic, bookId.toString(), kafkaEvent);

        log.info("Published IndexingEvent to Kafka for bookId: {}, jobId: {}", bookId, job.getId());
        return job;
    }
}
