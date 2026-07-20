package ai.pustakalay.backend.retrieval.repository;

import ai.pustakalay.backend.retrieval.entity.RetrievedChunk;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface RetrievedChunkRepository extends JpaRepository<RetrievedChunk, UUID> {
    List<RetrievedChunk> findByRetrievalIdOrderByRankAsc(UUID retrievalId);
}
