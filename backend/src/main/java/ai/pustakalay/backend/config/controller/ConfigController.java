package ai.pustakalay.backend.config.controller;

import ai.pustakalay.backend.config.entity.SystemConfiguration;
import ai.pustakalay.backend.config.repository.SystemConfigurationRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import jakarta.annotation.PostConstruct;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/config")
@RequiredArgsConstructor
public class ConfigController {

    private final SystemConfigurationRepository systemConfigurationRepository;

    @PostConstruct
    public void initDefaults() {
        initKey("embedding_model", "nomic-embed-text");
        initKey("chat_model", "qwen3");
        initKey("top_k", "10");
        initKey("temperature", "0.2");
    }

    private void initKey(String key, String defaultValue) {
        if (!systemConfigurationRepository.existsById(key)) {
            systemConfigurationRepository.save(SystemConfiguration.builder()
                    .key(key)
                    .value(defaultValue)
                    .build());
        }
    }

    @GetMapping
    public ResponseEntity<List<SystemConfiguration>> getConfigurations() {
        return ResponseEntity.ok(systemConfigurationRepository.findAll());
    }

    @PostMapping
    public ResponseEntity<SystemConfiguration> updateConfiguration(@RequestBody Map<String, String> payload) {
        String key = payload.get("key");
        String value = payload.get("value");
        if (key == null || value == null) {
            return ResponseEntity.badRequest().build();
        }

        SystemConfiguration config = SystemConfiguration.builder()
                .key(key)
                .value(value)
                .build();
        return ResponseEntity.ok(systemConfigurationRepository.save(config));
    }
}
