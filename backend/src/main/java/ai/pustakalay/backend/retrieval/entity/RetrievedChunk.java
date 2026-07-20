package ai.pustakalay.backend.retrieval.entity;

import jakarta.persistence.*;
import lombok.*;

import java.util.UUID;

@Entity
@Table(name = "retrieved_chunk")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class RetrievedChunk {

    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    private UUID id;

    @Column(name = "retrieval_id", nullable = false)
    private UUID retrievalId;

    @Column(name = "chunk_id", nullable = false)
    private UUID chunkId;

    @Column(name = "similarity_score")
    private Double similarityScore;

    @Column(name = "rank")
    private Integer rank;
}
