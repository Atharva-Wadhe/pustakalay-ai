package ai.pustakalay.backend.books.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "document_event")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class DocumentEvent {

    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    private UUID id;

    @Column(name = "document_id", nullable = false)
    private UUID documentId;

    @Column(name = "event_type", nullable = false)
    @Enumerated(EnumType.STRING)
    private EventType eventType;

    @Column(name = "event_payload", columnDefinition = "TEXT")
    private String eventPayload;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    public enum EventType {
        DISCOVERED,
        REGISTERED,
        METADATA_UPDATED,
        INDEX_REQUESTED,
        INDEX_STARTED,
        INDEX_COMPLETED,
        INDEX_FAILED,
        CHAT_REFERENCED
    }
}
