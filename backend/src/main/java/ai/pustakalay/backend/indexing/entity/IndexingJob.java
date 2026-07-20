package ai.pustakalay.backend.indexing.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "indexing_job")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class IndexingJob {

    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    private UUID id;

    @Column(name = "document_id", nullable = false)
    private UUID documentId;

    @Column(name = "trigger_type", nullable = false)
    @Enumerated(EnumType.STRING)
    private TriggerType triggerType;

    @Column(nullable = false)
    @Enumerated(EnumType.STRING)
    private JobStatus status;

    @CreationTimestamp
    @Column(name = "queued_at", updatable = false)
    private LocalDateTime queuedAt;

    @Column(name = "started_at")
    private LocalDateTime startedAt;

    @Column(name = "completed_at")
    private LocalDateTime completedAt;

    private Integer progress;

    @Column(name = "error_message", columnDefinition = "TEXT")
    private String errorMessage;

    @Column(name = "chunk_count")
    private Integer chunkCount;

    @Column(name = "embedding_model")
    private String embeddingModel;

    @Column(name = "chunk_strategy")
    private String chunkStrategy;

    @Column(name = "chunk_size")
    private Integer chunkSize;

    @Column(name = "chunk_overlap")
    private Integer chunkOverlap;

    @Column(name = "index_version")
    private Integer indexVersion;

    public enum TriggerType {
        AUTO, MANUAL, REINDEX
    }

    public enum JobStatus {
        QUEUED, RUNNING, COMPLETED, FAILED
    }
}
