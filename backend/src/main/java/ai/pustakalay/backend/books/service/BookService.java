package ai.pustakalay.backend.books.service;

import ai.pustakalay.backend.books.entity.Document;
import ai.pustakalay.backend.books.entity.DocumentEvent;
import ai.pustakalay.backend.books.repository.DocumentEventRepository;
import ai.pustakalay.backend.books.repository.DocumentRepository;
import ai.pustakalay.backend.common.service.FileStorageService;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.File;
import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

@Service
@Slf4j
@RequiredArgsConstructor
public class BookService {

    private final DocumentRepository documentRepository;
    private final DocumentEventRepository documentEventRepository;
    private final FileStorageService fileStorageService;
    private final ObjectMapper objectMapper;
    private final ApplicationEventPublisher eventPublisher;

    public List<Document> listAll() {
        return documentRepository.findAll();
    }

    public Optional<Document> getById(UUID id) {
        return documentRepository.findById(id);
    }

    @Transactional
    public Document registerBook(String filePath, String title, String author, String category) throws IOException {
        File file = new File(filePath);
        if (!file.exists()) {
            throw new IllegalArgumentException("File does not exist: " + filePath);
        }

        String fileHash = fileStorageService.calculateSHA256(file);
        Optional<Document> existingOpt = documentRepository.findByFileHash(fileHash);
        if (existingOpt.isPresent()) {
            log.info("Book with hash {} already registered: {}", fileHash, existingOpt.get().getTitle());
            return existingOpt.get();
        }

        Document document = Document.builder()
                .documentType(Document.DocumentType.BOOK)
                .title(title != null ? title : file.getName())
                .author(author != null ? author : "Unknown")
                .category(category != null ? category : "General")
                .fileName(file.getName())
                .filePath(filePath)
                .fileSize(file.length())
                .fileHash(fileHash)
                .status(Document.DocumentStatus.DISCOVERED)
                .metadataStatus(Document.DocumentStatus.DISCOVERED)
                .build();

        document = documentRepository.save(document);
        logEvent(document.getId(), DocumentEvent.EventType.DISCOVERED, Map.of("filePath", filePath));
        logEvent(document.getId(), DocumentEvent.EventType.REGISTERED, Map.of("title", document.getTitle()));

        log.info("Registered new book: {} (ID: {})", document.getTitle(), document.getId());

        // Publish an internal Spring event to trigger indexing
        eventPublisher.publishEvent(new BookRegisteredEvent(document.getId()));

        return document;
    }

    @Transactional
    public Document updateMetadata(UUID id, Document updated) {
        Document document = documentRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Document not found: " + id));

        document.setTitle(updated.getTitle());
        document.setAuthor(updated.getAuthor());
        document.setPublisher(updated.getPublisher());
        document.setIsbn(updated.getIsbn());
        document.setCategory(updated.getCategory());
        document.setLanguage(updated.getLanguage());
        document.setDescription(updated.getDescription());
        document.setPageCount(updated.getPageCount());

        document = documentRepository.save(document);
        logEvent(id, DocumentEvent.EventType.METADATA_UPDATED, Map.of("updatedBy", "User"));
        return document;
    }

    @Transactional
    public void deleteBook(UUID id) {
        Document document = documentRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Document not found: " + id));

        // Publish event to delete from Qdrant and database chunks
        eventPublisher.publishEvent(new BookDeletedEvent(id));

        // Delete physical file
        fileStorageService.deleteFile(document.getFilePath());

        // Update status or delete from DB
        document.setStatus(Document.DocumentStatus.DELETED);
        documentRepository.save(document);

        logEvent(id, DocumentEvent.EventType.INDEX_FAILED, Map.of("reason", "Deleted by user"));
        log.info("Marked book as deleted: {} (ID: {})", document.getTitle(), id);
    }

    public List<DocumentEvent> getEvents(UUID documentId) {
        return documentEventRepository.findByDocumentIdOrderByCreatedAtDesc(documentId);
    }

    public void logEvent(UUID documentId, DocumentEvent.EventType eventType, Map<String, Object> payload) {
        try {
            String payloadStr = objectMapper.writeValueAsString(payload);
            DocumentEvent event = DocumentEvent.builder()
                    .documentId(documentId)
                    .eventType(eventType)
                    .eventPayload(payloadStr)
                    .build();
            documentEventRepository.save(event);
        } catch (Exception e) {
            log.error("Failed to log document event", e);
        }
    }

    // Custom Spring Events for decoupled communication
    public static class BookRegisteredEvent {
        private final UUID bookId;

        public BookRegisteredEvent(UUID bookId) {
            this.bookId = bookId;
        }

        public UUID getBookId() {
            return bookId;
        }
    }

    public static class BookDeletedEvent {
        private final UUID bookId;

        public BookDeletedEvent(UUID bookId) {
            this.bookId = bookId;
        }

        public UUID getBookId() {
            return bookId;
        }
    }
}
