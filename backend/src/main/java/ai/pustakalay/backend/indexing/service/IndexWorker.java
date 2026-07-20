package ai.pustakalay.backend.indexing.service;

import ai.pustakalay.backend.books.entity.Document;
import ai.pustakalay.backend.books.entity.DocumentEvent;
import ai.pustakalay.backend.books.repository.DocumentRepository;
import ai.pustakalay.backend.books.service.BookService;
import ai.pustakalay.backend.common.service.FileStorageService;
import ai.pustakalay.backend.indexing.entity.DocumentChunk;
import ai.pustakalay.backend.indexing.entity.IndexingJob;
import ai.pustakalay.backend.indexing.event.IndexingEvent;
import ai.pustakalay.backend.indexing.repository.DocumentChunkRepository;
import ai.pustakalay.backend.indexing.repository.IndexingJobRepository;
import ai.pustakalay.backend.indexing.strategy.Chunk;
import ai.pustakalay.backend.indexing.strategy.ParsedPage;
import ai.pustakalay.backend.indexing.strategy.SemanticChunkingStrategy;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Service;

import java.io.File;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Service
@Slf4j
@RequiredArgsConstructor
public class IndexWorker {

    private final DocumentRepository documentRepository;
    private final IndexingJobRepository indexingJobRepository;
    private final DocumentChunkRepository documentChunkRepository;
    private final BookService bookService;
    private final FileStorageService fileStorageService;
    private final SemanticChunkingStrategy semanticChunkingStrategy;
    private final VectorStore vectorStore;

    @KafkaListener(topics = "${app.kafka.topics.indexing-jobs}", groupId = "pustakalay-group")
    public void consumeIndexingJob(IndexingEvent event) {
        log.info("Received indexing job from Kafka. BookId: {}, JobId: {}", event.getBookId(), event.getJobId());

        IndexingJob job = indexingJobRepository.findById(event.getJobId()).orElse(null);
        Document document = documentRepository.findById(event.getBookId()).orElse(null);

        if (job == null || document == null) {
            log.error("Job or Document not found. JobId: {}, BookId: {}", event.getJobId(), event.getBookId());
            return;
        }

        try {
            processIndexing(document, job);
        } catch (Exception e) {
            log.error("Failed to index document: {}", document.getTitle(), e);
            failJob(document, job, e.getMessage());
        }
    }

    private void processIndexing(Document document, IndexingJob job) throws Exception {
        // 1. Update status to RUNNING
        job.setStatus(IndexingJob.JobStatus.RUNNING);
        job.setStartedAt(LocalDateTime.now());
        job.setProgress(5);
        indexingJobRepository.save(job);

        bookService.logEvent(document.getId(), DocumentEvent.EventType.INDEX_STARTED, Map.of("jobId", job.getId()));

        // 2. Parse PDF
        log.info("Parsing PDF: {}", document.getFilePath());
        List<ParsedPage> parsedPages = new ArrayList<>();

        File file = new File(document.getFilePath());
        try (PDDocument pdfDoc = Loader.loadPDF(file)) {
            int totalPages = pdfDoc.getNumberOfPages();
            document.setPageCount(totalPages);
            documentRepository.save(document);

            PDFTextStripper stripper = new PDFTextStripper();
            for (int i = 1; i <= totalPages; i++) {
                stripper.setStartPage(i);
                stripper.setEndPage(i);
                String text = stripper.getText(pdfDoc);
                parsedPages.add(new ParsedPage(i, text));

                // Update progress during parsing (up to 30%)
                int progress = 5 + (int) (((double) i / totalPages) * 25);
                updateJobProgress(job, progress);
            }
        }

        // 3. Chunk PDF
        log.info("Chunking document: {}", document.getTitle());
        List<Chunk> chunks = semanticChunkingStrategy.chunk(parsedPages, job.getChunkSize(), job.getChunkOverlap());
        job.setChunkCount(chunks.size());
        indexingJobRepository.save(job);
        updateJobProgress(job, 40);

        // 4. Generate Embeddings & Save to Qdrant + PostgreSQL
        log.info("Generating embeddings and saving chunks for document: {}", document.getTitle());
        List<org.springframework.ai.document.Document> aiDocs = new ArrayList<>();

        for (int i = 0; i < chunks.size(); i++) {
            Chunk chunk = chunks.get(i);

            // Save Chunk metadata to PostgreSQL
            DocumentChunk dbChunk = DocumentChunk.builder()
                    .documentId(document.getId())
                    .chunkNumber(chunk.getChunkNumber())
                    .chapter(chunk.getChapter())
                    .section(chunk.getSection())
                    .pageStart(chunk.getPageStart())
                    .pageEnd(chunk.getPageEnd())
                    .tokenCount(chunk.getTokenCount())
                    .characterCount(chunk.getCharacterCount())
                    .textHash(fileStorageService.calculateSHA256(new File(document.getFilePath()))) // simple hash
                                                                                                    // placeholder or
                                                                                                    // text hash
                    .build();

            dbChunk = documentChunkRepository.save(dbChunk);

            // Prepare AI Document for Vector Store
            org.springframework.ai.document.Document aiDoc = new org.springframework.ai.document.Document(
                    dbChunk.getId().toString(),
                    chunk.getText(),
                    Map.of(
                            "documentId", document.getId().toString(),
                            "chunkId", dbChunk.getId().toString(),
                            "pageStart", chunk.getPageStart(),
                            "pageEnd", chunk.getPageEnd(),
                            "chapter", chunk.getChapter(),
                            "section", chunk.getSection(),
                            "category", document.getCategory(),
                            "title", document.getTitle(),
                            "indexVersion", job.getIndexVersion()));
            aiDocs.add(aiDoc);

            // Update progress during vector processing (40% to 90%)
            int progress = 40 + (int) (((double) (i + 1) / chunks.size()) * 50);
            updateJobProgress(job, progress);
        }

        // Batch add to Qdrant
        if (!aiDocs.isEmpty()) {
            vectorStore.add(aiDocs);
        }

        // 5. Move file to Indexed folder
        String newPath = fileStorageService.moveToIndexed(document.getFilePath());

        // 6. Complete Job
        document.setFilePath(newPath);
        document.setStatus(Document.DocumentStatus.INDEXED);
        document.setMetadataStatus(Document.DocumentStatus.INDEXED);
        documentRepository.save(document);

        job.setStatus(IndexingJob.JobStatus.COMPLETED);
        job.setCompletedAt(LocalDateTime.now());
        job.setProgress(100);
        indexingJobRepository.save(job);

        bookService.logEvent(document.getId(), DocumentEvent.EventType.INDEX_COMPLETED,
                Map.of("jobId", job.getId(), "chunksCount", chunks.size()));

        log.info("Successfully completed indexing for document: {}", document.getTitle());
    }

    private void updateJobProgress(IndexingJob job, int progress) {
        job.setProgress(progress);
        indexingJobRepository.save(job);
    }

    private void failJob(Document document, IndexingJob job, String errorMessage) {
        try {
            document.setStatus(Document.DocumentStatus.FAILED);
            document.setMetadataStatus(Document.DocumentStatus.FAILED);
            documentRepository.save(document);

            job.setStatus(IndexingJob.JobStatus.FAILED);
            job.setCompletedAt(LocalDateTime.now());
            job.setErrorMessage(errorMessage);
            indexingJobRepository.save(job);

            bookService.logEvent(document.getId(), DocumentEvent.EventType.INDEX_FAILED,
                    Map.of("jobId", job.getId(), "error", errorMessage));
        } catch (Exception ex) {
            log.error("Failed to update job failure status", ex);
        }
    }
}
