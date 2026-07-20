package ai.pustakalay.backend.books.controller;

import ai.pustakalay.backend.books.entity.Document;
import ai.pustakalay.backend.books.entity.DocumentEvent;
import ai.pustakalay.backend.books.service.BookService;
import ai.pustakalay.backend.indexing.entity.IndexingJob;
import ai.pustakalay.backend.indexing.service.IndexingOrchestrator;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.io.IOException;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/books")
@RequiredArgsConstructor
public class BookController {

    private final BookService bookService;
    private final IndexingOrchestrator indexingOrchestrator;

    @GetMapping
    public ResponseEntity<List<Document>> getAllBooks() {
        return ResponseEntity.ok(bookService.listAll());
    }

    @GetMapping("/{id}")
    public ResponseEntity<Document> getBookById(@PathVariable UUID id) {
        return bookService.getById(id)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @PostMapping("/register")
    public ResponseEntity<Document> registerBook(@RequestBody RegisterBookRequest request) {
        try {
            Document doc = bookService.registerBook(
                    request.getFilePath(),
                    request.getTitle(),
                    request.getAuthor(),
                    request.getCategory());
            return ResponseEntity.ok(doc);
        } catch (IOException e) {
            return ResponseEntity.badRequest().build();
        }
    }

    @PutMapping("/{id}")
    public ResponseEntity<Document> updateMetadata(@PathVariable UUID id, @RequestBody Document document) {
        try {
            return ResponseEntity.ok(bookService.updateMetadata(id, document));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.notFound().build();
        }
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteBook(@PathVariable UUID id) {
        try {
            bookService.deleteBook(id);
            return ResponseEntity.noContent().build();
        } catch (IllegalArgumentException e) {
            return ResponseEntity.notFound().build();
        }
    }

    @GetMapping("/{id}/events")
    public ResponseEntity<List<DocumentEvent>> getBookEvents(@PathVariable UUID id) {
        return ResponseEntity.ok(bookService.getEvents(id));
    }

    @PostMapping("/{id}/reindex")
    public ResponseEntity<IndexingJob> reindexBook(@PathVariable UUID id) {
        try {
            IndexingJob job = indexingOrchestrator.triggerIndexing(id, IndexingJob.TriggerType.REINDEX);
            return ResponseEntity.ok(job);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.notFound().build();
        }
    }

    @Data
    public static class RegisterBookRequest {
        private String filePath;
        private String title;
        private String author;
        private String category;
    }
}
