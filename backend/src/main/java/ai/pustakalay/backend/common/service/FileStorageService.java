package ai.pustakalay.backend.common.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import jakarta.annotation.PostConstruct;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

@Service
@Slf4j
public class FileStorageService {

    @Value("${app.storage.incoming-dir}")
    private String incomingDir;

    @Value("${app.storage.indexed-dir}")
    private String indexedDir;

    @PostConstruct
    public void init() {
        try {
            Files.createDirectories(Paths.get(incomingDir));
            Files.createDirectories(Paths.get(indexedDir));
            log.info("Storage directories initialized: Incoming={}, Indexed={}", incomingDir, indexedDir);
        } catch (IOException e) {
            log.error("Failed to create storage directories", e);
            throw new RuntimeException("Could not initialize storage directories", e);
        }
    }

    public String calculateSHA256(File file) throws IOException {
        MessageDigest digest;
        try {
            digest = MessageDigest.getInstance("SHA-256");
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException("SHA-256 algorithm not found", e);
        }

        try (FileInputStream fis = new FileInputStream(file)) {
            byte[] byteArray = new byte[1024];
            int bytesCount;
            while ((bytesCount = fis.read(byteArray)) != -1) {
                digest.update(byteArray, 0, bytesCount);
            }
        }

        byte[] bytes = digest.digest();
        StringBuilder sb = new StringBuilder();
        for (byte b : bytes) {
            sb.append(Integer.toString((b & 0xff) + 0x100, 16).substring(1));
        }
        return sb.toString();
    }

    public String moveToIndexed(String currentPathStr) throws IOException {
        Path currentPath = Paths.get(currentPathStr);
        if (!currentPath.startsWith(Paths.get(incomingDir))) {
            log.warn("File {} is not in incoming directory, skipping move", currentPathStr);
            return currentPathStr;
        }

        Path targetPath = Paths.get(indexedDir).resolve(currentPath.getFileName());
        Files.move(currentPath, targetPath, StandardCopyOption.REPLACE_EXISTING);
        log.info("Moved file from {} to {}", currentPathStr, targetPath);
        return targetPath.toString();
    }

    public void deleteFile(String filePathStr) {
        try {
            Path path = Paths.get(filePathStr);
            if (Files.exists(path)) {
                Files.delete(path);
                log.info("Deleted file: {}", filePathStr);
            } else {
                log.warn("File to delete does not exist: {}", filePathStr);
            }
        } catch (IOException e) {
            log.error("Failed to delete file: {}", filePathStr, e);
        }
    }
}
