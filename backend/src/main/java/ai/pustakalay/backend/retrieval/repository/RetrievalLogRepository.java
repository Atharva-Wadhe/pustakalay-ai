package ai.pustakalay.backend.retrieval.repository;

import ai.pustakalay.backend.retrieval.entity.RetrievalLog;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface RetrievalLogRepository extends JpaRepository<RetrievalLog, UUID> {
    Optional<RetrievalLog> findByMessageId(UUID messageId);
}
