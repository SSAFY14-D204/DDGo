package com.ssafy.DDGo.global.config;

import io.swagger.v3.oas.annotations.OpenAPIDefinition;
import io.swagger.v3.oas.annotations.info.Info;
import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.springdoc.core.models.GroupedOpenApi;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;

@Configuration
@OpenAPIDefinition(info = @Info(title = "DDGo API 명세서", description = "SSAFY 특화 프로젝트 DDGo API 명세서", version = "v1.0"))
public class SwaggerConfig {

        // ── 공통 Security Scheme (Bearer JWT) ──────────────────────────────
        @Bean
        public OpenAPI openAPI() {
                SecurityScheme securityScheme = new SecurityScheme()
                                .type(SecurityScheme.Type.HTTP)
                                .scheme("bearer")
                                .bearerFormat("JWT")
                                .in(SecurityScheme.In.HEADER)
                                .name("Authorization");

                SecurityRequirement securityRequirement = new SecurityRequirement().addList("bearerAuth");

                return new OpenAPI()
                                .components(new Components().addSecuritySchemes("bearerAuth", securityScheme))
                                .security(List.of(securityRequirement));
        }

        // ── 탭 1: Users ────────────────────────────────────────────────────
        @Bean
        public GroupedOpenApi usersApi() {
                return GroupedOpenApi.builder()
                                .group("1. Users")
                                .pathsToMatch("/v1/users/**")
                                .build();
        }

        // ── 탭 2: Challenges ───────────────────────────────────────────────
        @Bean
        public GroupedOpenApi challengesApi() {
                return GroupedOpenApi.builder()
                                .group("2. Challenges")
                                .pathsToMatch("/v1/challenges/**")
                                .pathsToExclude("/v1/challenges/*/attempts/**")
                                .build();
        }

        // ── 탭 3: Attempts ─────────────────────────────────────────────────
        @Bean
        public GroupedOpenApi attemptsApi() {
                return GroupedOpenApi.builder()
                                .group("3. Attempts")
                                .pathsToMatch("/v1/challenges/*/attempts/**", "/v1/attempts/**")
                                .build();
        }

        // ── 탭 4: Gyms ─────────────────────────────────────────────────────
        @Bean
        public GroupedOpenApi gymsApi() {
                return GroupedOpenApi.builder()
                                .group("4. Gyms")
                                .pathsToMatch("/api/gyms/**")
                                .build();
        }
}
