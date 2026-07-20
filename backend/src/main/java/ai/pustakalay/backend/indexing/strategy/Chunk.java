package ai.pustakalay.backend.indexing.strategy;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class Chunk {
    private String text;
    private int chunkNumber;
    private String chapter;
    private String section;
    private int pageStart;
    private int pageEnd;
    private int paragraphStart;
    private int paragraphEnd;
    private int tokenCount;
    private int characterCount;
}
