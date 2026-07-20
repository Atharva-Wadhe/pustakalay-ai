package ai.pustakalay.backend.indexing.repository;

import ai.pustakalay.backend.indexing.entity.IndexingJob;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface IndexingJobRepository extends JpaRepository<IndexingJob, UUID> {
    List<IndexingJob> findByDocumentIdOrderByQueuedAtDesc(UUID documentId);
}
