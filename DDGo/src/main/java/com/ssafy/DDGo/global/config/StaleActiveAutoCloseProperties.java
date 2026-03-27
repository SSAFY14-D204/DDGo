package com.ssafy.DDGo.global.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Getter
@Setter
@Configuration
@ConfigurationProperties(prefix = "challenge.stale-active-auto-close")
public class StaleActiveAutoCloseProperties {

    private boolean enabled = true;
    private int staleAfterHours = 6;
    private long intervalMs = 3_600_000L;
    private int batchSize = 200;
}
