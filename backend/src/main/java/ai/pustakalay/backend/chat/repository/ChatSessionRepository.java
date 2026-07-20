package ai.pustakalay.backend.chat.repository;

import ai.pustakalay.backend.chat.entity.ChatSession;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface ChatSessionRepository extends JpaRepository<ChatSession, UUID> {
    List<ChatSession> findAllByOrderByUpdatedAtDesc();
}
