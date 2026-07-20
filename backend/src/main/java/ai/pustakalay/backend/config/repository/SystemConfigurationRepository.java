package ai.pustakalay.backend.config.repository;

import ai.pustakalay.backend.config.entity.SystemConfiguration;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface SystemConfigurationRepository extends JpaRepository<SystemConfiguration, String> {
}
