package ai.pustakalay.backend.books.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "document")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Document {

    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    private UUID id;

    @Column(name = "document_type")
    @Enumerated(EnumType.STRING)
    private DocumentType documentType;

    private String title;
    private String author;
    private String publisher;
    private String isbn;
    private String category;
    private String language;

    @Column(columnDefinition = "TEXT")
    private String description;

    @Column(name = "file_name")
    private String fileName;

    @Column(name = "file_path")
    private String filePath;

    @Column(name = "file_size")
    private Long fileSize;

    @Column(name = "file_hash")
    private String fileHash;

    @Column(name = "page_count")
    private Integer pageCount;

    @Column(name = "cover_image_path")
    private String coverImagePath;

    @Column(name = "metadata_status")
    @Enumerated(EnumType.STRING)
    private DocumentStatus metadataStatus;

    @Column(name = "metadata_source")
    private String metadataSource;

    @Column(name = "metadata_confidence")
    private Double metadataConfidence;

    @Enumerated(EnumType.STRING)
    private DocumentStatus status;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    public enum DocumentType {
        BOOK, PAPER, NOTE
    }

    public enum DocumentStatus {
        DISCOVERED,
        READY_TO_INDEX,
        INDEXING,
        INDEXED,
        FAILED,
        DELETED
    }
}
