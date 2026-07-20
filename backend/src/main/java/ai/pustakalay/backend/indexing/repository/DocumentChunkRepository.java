package ai.pustakalay.backend.indexing.repository;

import ai.pustakalay.backend.indexing.entity.DocumentChunk;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Repository
public interface DocumentChunkRepository extends JpaRepository<DocumentChunk, UUID> {
    List<DocumentChunk> findByDocumentIdOrderByChunkNumberAsc(UUID documentId);

    @Transactional
    void deleteByDocumentId(UUID documentId);
}
