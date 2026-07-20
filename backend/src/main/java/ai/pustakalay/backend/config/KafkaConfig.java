package ai.pustakalay.backend.config;

import org.apache.kafka.clients.admin.NewTopic;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.TopicBuilder;

@Configuration
public class KafkaConfig {

    @Value("${app.kafka.topics.indexing-jobs}")
    private String indexingJobsTopic;

    @Bean
    public NewTopic indexingJobsTopic() {
        return TopicBuilder.name(indexingJobsTopic)
                .partitions(3)
                .replicas(1)
                .build();
    }
}
