package com.neueda.leap.team.seed;

import com.neueda.leap.team.config.SecuritySeedProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@EnableConfigurationProperties(SecuritySeedProperties.class)
@ConditionalOnProperty(prefix = "app.securities.seed", name = "enabled", havingValue = "true", matchIfMissing = true)
public class SecuritySeedRunner implements ApplicationRunner {
    private static final Logger log = LoggerFactory.getLogger(SecuritySeedRunner.class);
    private final SecuritySeedService service;
    private final SecuritySeedProperties properties;

    public SecuritySeedRunner(SecuritySeedService service, SecuritySeedProperties properties) {
        this.service = service; this.properties = properties;
    }
    @Override
    public void run(ApplicationArguments args) {
        var result = service.seed(properties.resources());
        log.info("Securities seed complete: {} selected, {} inserted, {} already present.",
                result.total(), result.inserted(), result.existing());
    }
}
