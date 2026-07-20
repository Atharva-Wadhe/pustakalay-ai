package ai.pustakalay.backend.indexing.service;

import ai.pustakalay.backend.books.entity.Document;
import ai.pustakalay.backend.books.repository.DocumentRepository;
import ai.pustakalay.backend.books.service.BookService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Optional;
import java.util.stream.Stream;

@Service
@Slf4j
@RequiredArgsConstructor
public class FileWatcherService {

    private final BookService bookService;
    private final DocumentRepository documentRepository;

    @Value("${app.storage.incoming-dir}")
    private String incomingDir;

    @Scheduled(fixedDelay = 5000) // Scan every 5 seconds
    public void scanIncomingDirectory() {
        Path incomingPath = Paths.get(incomingDir);
        if (!Files.exists(incomingPath)) {
            return;
        }

        try (Stream<Path> walk = Files.walk(incomingPath, 1)) {
            walk.filter(Files::isRegularFile)
                    .filter(path -> path.toString().toLowerCase().endsWith(".pdf"))
                    .forEach(this::processIncomingFile);
        } catch (IOException e) {
            log.error("Error scanning incoming directory: {}", incomingDir, e);
        }
    }

    private void processIncomingFile(Path filePath) {
        String absolutePath = filePath.toAbsolutePath().toString();

        // Check if file is still being written (size changes)
        if (isFileLockedOrWriting(filePath.toFile())) {
            log.debug("File is still being written or locked: {}", absolutePath);
            return;
        }

        // Check if already registered by file path
        Optional<Document> existing = documentRepository.findByFilePath(absolutePath);
        if (existing.isPresent()) {
            return;
        }

        log.info("Detected new PDF in incoming folder: {}", absolutePath);
        try {
            // Register book. This will calculate hash, save to DB, and trigger indexing.
            bookService.registerBook(absolutePath, null, null, "General");
        } catch (Exception e) {
            log.error("Failed to automatically register incoming file: {}", absolutePath, e);
        }
    }

    private boolean isFileLockedOrWriting(File file) {
        long sizeBefore = file.length();
        try {
            Thread.sleep(500); // Wait briefly to see if size changes
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        long sizeAfter = file.length();

        if (sizeBefore != sizeAfter) {
            return true;
        }

        // Try to open a read stream to check if it's locked
        try (java.io.FileInputStream fis = new java.io.FileInputStream(file)) {
            return false; // Successfully opened, not locked
        } catch (IOException e) {
            return true; // Locked
        }
    }
}
