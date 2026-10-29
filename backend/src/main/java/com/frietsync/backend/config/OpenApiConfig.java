package com.frietsync.backend.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.springdoc.core.customizers.OpenApiCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenApiConfig {

    private static final String BEARER_AUTH = "bearerAuth";

    @Bean
    public OpenAPI frietSyncOpenApi() {
        return new OpenAPI()
                .info(new Info()
                        .title("FrietSync API")
                        .description("API documentation for FrietSync backend")
                        .version("v1"))
                .components(new Components().addSecuritySchemes(BEARER_AUTH,
                        new SecurityScheme()
                                .type(SecurityScheme.Type.HTTP)
                                .scheme("bearer")
                                .bearerFormat("JWT")));
    }

    @Bean
    public OpenApiCustomizer jwtSecurityForProtectedEndpoints() {
        return openApi -> openApi.getPaths().forEach((path, pathItem) -> {
            boolean publicEndpoint = path.startsWith("/api/v1/auth/")
                    || path.equals("/api/v1/check");
            if (!publicEndpoint) {
                pathItem.readOperations().forEach(operation -> operation.addSecurityItem(
                        new SecurityRequirement().addList(BEARER_AUTH)));
            }
        });
    }
}
