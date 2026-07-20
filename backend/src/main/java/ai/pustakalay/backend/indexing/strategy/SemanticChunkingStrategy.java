package ai.pustakalay.backend.indexing.strategy;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Component
@Slf4j
public class SemanticChunkingStrategy implements ChunkingStrategy {

    private static final Pattern HEADING_PATTERN = Pattern.compile(
            "^(?:Chapter\\s+\\d+|\\d+\\.\\d*\\s+)([A-Z][a-zA-Z0-9\\s\\-_:]{3,50})$",
            Pattern.MULTILINE);

    @Override
    public String getStrategyName() {
        return "Semantic";
    }

    @Override
    public List<Chunk> chunk(List<ParsedPage> pages, int chunkSize, int chunkOverlap) {
        log.info("Starting semantic chunking with chunkSize={}, chunkOverlap={}", chunkSize, chunkOverlap);
        List<Chunk> chunks = new ArrayList<>();

        // 1. Extract paragraphs with metadata
        List<Paragraph> paragraphs = extractParagraphs(pages);
        if (paragraphs.isEmpty()) {
            return chunks;
        }

        // 2. Build chunks
        List<Paragraph> currentChunkParagraphs = new ArrayList<>();
        int currentTokenCount = 0;
        int chunkIndex = 1;

        String currentChapter = "Introduction";
        String currentSection = "General";

        for (int i = 0; i < paragraphs.size(); i++) {
            Paragraph p = paragraphs.get(i);

            // Update current chapter/section if a heading is detected
            if (p.isHeading) {
                if (p.text.toLowerCase().contains("chapter")) {
                    currentChapter = p.text;
                    currentSection = "Overview";
                } else {
                    currentSection = p.text;
                }
            }

            int pTokens = estimateTokens(p.text);

            // If adding this paragraph exceeds the chunk size and we already have content,
            // we finalize the current chunk.
            if (currentTokenCount + pTokens > chunkSize && !currentChunkParagraphs.isEmpty()) {
                chunks.add(buildChunk(currentChunkParagraphs, chunkIndex++, currentChapter, currentSection,
                        currentTokenCount));

                // Implement overlap: backtrack to include previous paragraphs
                List<Paragraph> overlapParagraphs = new ArrayList<>();
                int overlapTokens = 0;

                for (int j = currentChunkParagraphs.size() - 1; j >= 0; j--) {
                    Paragraph op = currentChunkParagraphs.get(j);
                    int opTokens = estimateTokens(op.text);
                    if (overlapTokens + opTokens <= chunkOverlap) {
                        overlapParagraphs.add(0, op);
                        overlapTokens += opTokens;
                    } else {
                        break;
                    }
                }

                currentChunkParagraphs = new ArrayList<>(overlapParagraphs);
                currentTokenCount = overlapTokens;
            }

            currentChunkParagraphs.add(p);
            currentTokenCount += pTokens;
        }

        // Add the last remaining chunk
        if (!currentChunkParagraphs.isEmpty()) {
            chunks.add(
                    buildChunk(currentChunkParagraphs, chunkIndex, currentChapter, currentSection, currentTokenCount));
        }

        log.info("Semantic chunking completed. Generated {} chunks.", chunks.size());
        return chunks;
    }

    private List<Paragraph> extractParagraphs(List<ParsedPage> pages) {
        List<Paragraph> paragraphs = new ArrayList<>();
        int globalParagraphIndex = 1;

        for (ParsedPage page : pages) {
            String[] rawParagraphs = page.getText().split("\\n\\s*\\n+");

            for (String rawP : rawParagraphs) {
                String cleanText = rawP.trim().replaceAll("\\s+", " ");
                if (cleanText.isEmpty() || cleanText.length() < 10) {
                    continue;
                }

                boolean isHeading = isHeading(cleanText);
                paragraphs.add(new Paragraph(
                        cleanText,
                        page.getPageNumber(),
                        globalParagraphIndex++,
                        isHeading));
            }
        }
        return paragraphs;
    }

    private boolean isHeading(String text) {
        if (text.length() > 100) {
            return false;
        }
        // Match standard heading patterns (e.g. "Chapter 1", "2.1 Introduction")
        Matcher matcher = HEADING_PATTERN.matcher(text);
        if (matcher.find()) {
            return true;
        }
        // Fallback: short uppercase lines
        return text.length() < 60 && text.equals(text.toUpperCase()) && text.matches("^[A-Z0-9\\s\\-_:.]+$");
    }

    private int estimateTokens(String text) {
        // Rough estimation: 1 token ≈ 4 characters or 0.75 words
        // Let's use word count * 1.3 for a conservative estimate
        String[] words = text.split("\\s+");
        return (int) Math.ceil(words.length * 1.3);
    }

    private Chunk buildChunk(List<Paragraph> paragraphs, int chunkNumber, String chapter, String section,
            int tokenCount) {
        StringBuilder sb = new StringBuilder();
        int pageStart = Integer.MAX_VALUE;
        int pageEnd = Integer.MIN_VALUE;
        int pStart = Integer.MAX_VALUE;
        int pEnd = Integer.MIN_VALUE;

        for (Paragraph p : paragraphs) {
            if (sb.length() > 0) {
                sb.append("\n\n");
            }
            sb.append(p.text);

            pageStart = Math.min(pageStart, p.pageNumber);
            pageEnd = Math.max(pageEnd, p.pageNumber);
            pStart = Math.min(pStart, p.globalIndex);
            pEnd = Math.max(pEnd, p.globalIndex);
        }

        String text = sb.toString();
        return Chunk.builder()
                .text(text)
                .chunkNumber(chunkNumber)
                .chapter(chapter)
                .section(section)
                .pageStart(pageStart)
                .pageEnd(pageEnd)
                .paragraphStart(pStart)
                .paragraphEnd(pEnd)
                .tokenCount(tokenCount)
                .characterCount(text.length())
                .build();
    }

    private static class Paragraph {
        String text;
        int pageNumber;
        int globalIndex;
        boolean isHeading;

        Paragraph(String text, int pageNumber, int globalIndex, boolean isHeading) {
            this.text = text;
            this.pageNumber = pageNumber;
            this.globalIndex = globalIndex;
            this.isHeading = isHeading;
        }
    }
}
