package ai.pustakalay.backend.qdrant.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import jakarta.annotation.PostConstruct;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
@Slf4j
@RequiredArgsConstructor
public class QdrantService {

    @Value("${spring.ai.vectorstore.qdrant.host:localhost}")
    private String host;

    @Value("${spring.ai.vectorstore.qdrant.port:6334}")
    private int grpcPort;

    @Value("${spring.ai.vectorstore.qdrant.collection-name:library_chunks}")
    private String collectionName;

    private final RestTemplate restTemplate = new RestTemplate();
    private String restUrl;

    @PostConstruct
    public void init() {
        // Qdrant REST API is typically on port 6333
        this.restUrl = "http://" + host + ":6333";
        log.info("Qdrant REST URL initialized to: {}", restUrl);
        createCollectionIfNotExists();
    }

    public void createCollectionIfNotExists() {
        try {
            String url = restUrl + "/collections/" + collectionName;
            // Check if collection exists
            try {
                restTemplate.getForObject(url, Map.class);
                log.info("Qdrant collection '{}' already exists", collectionName);
                return;
            } catch (Exception e) {
                log.info("Qdrant collection '{}' does not exist, creating it...", collectionName);
            }

            // Create collection
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);

            // nomic-embed-text has 768 dimensions
            Map<String, Object> body = new HashMap<>();
            Map<String, Object> vectors = new HashMap<>();
            vectors.put("size", 768);
            vectors.put("distance", "Cosine");
            body.put("vectors", vectors);

            HttpEntity<Map<String, Object>> entity = new HttpEntity<>(body, headers);
            restTemplate.put(url, entity);
            log.info("Successfully created Qdrant collection '{}'", collectionName);
        } catch (Exception e) {
            log.error("Failed to create Qdrant collection", e);
        }
    }

    public void deletePointsByDocumentId(UUID documentId) {
        try {
            String url = restUrl + "/collections/" + collectionName + "/points/delete";
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);

            // Construct filter payload
            Map<String, Object> filter = new HashMap<>();
            Map<String, Object> match = new HashMap<>();
            match.put("value", documentId.toString());
            
            Map<String, Object> condition = new HashMap<>();
            condition.put("key", "documentId");
            condition.put("match", match);
            
            filter.put("must", List.of(condition));
            
            Map<String, Object> body = new HashMap<>();
            body.put("filter", filter);

            HttpEntity<Map<String, Object>> entity = new HttpEntity<>(body, headers);
            restTemplate.postForObject(url, entity, Map.class);
            log.info("Deleted all points in Qdrant for documentId: {}", documentId);
        } catch (Exception e) {
            log.error("Failed to delete points from Qdrant for documentId: {}", documentId, e);
        }
    }
}
