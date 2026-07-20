package ai.pustakalay.backend.retrieval.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "retrieval_log")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class RetrievalLog {

    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    private UUID id;

    @Column(name = "message_id")
    private UUID messageId;

    @Column(columnDefinition = "TEXT", nullable = false)
    private String query;

    @Column(name = "top_k")
    private Integer topK;

    @Column(name = "retrieval_ms")
    private Integer retrievalMs;

    @Column(name = "generation_ms")
    private Integer generationMs;

    private Integer tokens;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;
}
