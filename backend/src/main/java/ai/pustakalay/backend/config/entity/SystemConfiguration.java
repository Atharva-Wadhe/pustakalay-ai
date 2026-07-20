package ai.pustakalay.backend.config.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.*;

@Entity
@Table(name = "system_configuration")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class SystemConfiguration {

    @Id
    @Column(name = "config_key")
    private String key;

    @Column(name = "config_value", columnDefinition = "TEXT")
    private String value;
}
