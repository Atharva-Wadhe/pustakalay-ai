package ai.pustakalay.backend.indexing.event;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.UUID;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class IndexingEvent {
    private UUID bookId;
    private UUID jobId;
}
