package ai.pustakalay.backend.indexing.strategy;

import java.util.List;

public interface ChunkingStrategy {
    List<Chunk> chunk(List<ParsedPage> pages, int chunkSize, int chunkOverlap);

    String getStrategyName();
}
