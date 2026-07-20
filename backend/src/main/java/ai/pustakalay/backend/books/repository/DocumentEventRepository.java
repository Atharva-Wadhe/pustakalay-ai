package ai.pustakalay.backend.books.repository;

import ai.pustakalay.backend.books.entity.DocumentEvent;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface DocumentEventRepository extends JpaRepository<DocumentEvent, UUID> {
    List<DocumentEvent> findByDocumentIdOrderByCreatedAtDesc(UUID documentId);
}
