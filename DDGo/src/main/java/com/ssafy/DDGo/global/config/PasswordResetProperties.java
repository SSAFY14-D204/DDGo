package com.ssafy.DDGo.global.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Getter
@Setter
@Configuration
@ConfigurationProperties(prefix = "password-reset")
public class PasswordResetProperties {

    private boolean enabled = false;
    private String from;
    private String resetUrl = "http://localhost:3000/reset-password";
    private long tokenTtlSeconds = 900L;
    private long requestCooldownSeconds = 60L;
}
