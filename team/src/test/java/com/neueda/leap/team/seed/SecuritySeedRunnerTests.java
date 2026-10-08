package com.neueda.leap.team.seed;

import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.boot.DefaultApplicationArguments;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import static org.assertj.core.api.Assertions.*;

class SecuritySeedRunnerTests {
    @Test void startupSeedingCanBeDisabledWithoutConfiguringResources() {
        var service = new RecordingSeedService();
        new ApplicationContextRunner().withUserConfiguration(SecuritySeedRunner.class)
                .withBean(SecuritySeedService.class, () -> service)
                .withPropertyValues("app.securities.seed.enabled=false")
                .run(context -> {
                    assertThat(context).hasNotFailed().doesNotHaveBean(SecuritySeedRunner.class);
                    assertThat(service.calls).isZero();
                });
    }

    @Test void enabledRunnerUsesTheConfiguredResources() {
        var service = new RecordingSeedService();
        var files = List.of("classpath:universes/dev-v1.json");
        new ApplicationContextRunner().withUserConfiguration(SecuritySeedRunner.class)
                .withBean(SecuritySeedService.class, () -> service)
                .withPropertyValues("app.securities.seed.enabled=true", "app.securities.seed.resources=" + files.get(0))
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    context.getBean(SecuritySeedRunner.class).run(new DefaultApplicationArguments());
                    assertThat(service.calls).isEqualTo(1);
                    assertThat(service.resources).isEqualTo(files);
                });
    }

    private static class RecordingSeedService extends SecuritySeedService {
        private int calls;
        private List<String> resources;
        RecordingSeedService() { super(null, null); }
        @Override public SeedResult seed(List<String> resources) {
            this.calls++; this.resources = resources;
            return new SeedResult(20, 20, 0);
        }
    }
}
