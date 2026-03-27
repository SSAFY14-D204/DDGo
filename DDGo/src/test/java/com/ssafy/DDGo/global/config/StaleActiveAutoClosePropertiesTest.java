package com.ssafy.DDGo.global.config;

import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Configuration;

import static org.assertj.core.api.Assertions.assertThat;

class StaleActiveAutoClosePropertiesTest {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withUserConfiguration(TestConfig.class);

    @Test
    void bindsDefaultValues() {
        contextRunner.run(context -> {
            StaleActiveAutoCloseProperties properties = context.getBean(StaleActiveAutoCloseProperties.class);

            assertThat(properties.isEnabled()).isTrue();
            assertThat(properties.getStaleAfterHours()).isEqualTo(6);
            assertThat(properties.getIntervalMs()).isEqualTo(3_600_000L);
            assertThat(properties.getBatchSize()).isEqualTo(200);
        });
    }

    @Test
    void bindsExplicitValues() {
        contextRunner
                .withPropertyValues(
                        "challenge.stale-active-auto-close.enabled=true",
                        "challenge.stale-active-auto-close.stale-after-hours=12",
                        "challenge.stale-active-auto-close.interval-ms=900000",
                        "challenge.stale-active-auto-close.batch-size=75")
                .run(context -> {
                    StaleActiveAutoCloseProperties properties = context.getBean(StaleActiveAutoCloseProperties.class);

                    assertThat(properties.isEnabled()).isTrue();
                    assertThat(properties.getStaleAfterHours()).isEqualTo(12);
                    assertThat(properties.getIntervalMs()).isEqualTo(900000L);
                    assertThat(properties.getBatchSize()).isEqualTo(75);
                });
    }

    @Configuration
    @EnableConfigurationProperties(StaleActiveAutoCloseProperties.class)
    static class TestConfig {
    }
}
