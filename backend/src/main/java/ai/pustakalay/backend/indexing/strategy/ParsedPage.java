package ai.pustakalay.backend.indexing.strategy;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class ParsedPage {
    private int pageNumber;
    private String text;
}
