package com.suncorp.securehub.config;

import io.swagger.v3.oas.annotations.enums.SecuritySchemeType;
import io.swagger.v3.oas.annotations.security.SecurityScheme;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.*;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@SecurityScheme(
    name = "Bearer Authentication",
    type = SecuritySchemeType.HTTP,
    bearerFormat = "JWT",
    scheme = "bearer"
)
public class OpenApiConfig {

    @Bean
    public OpenAPI openAPI() {
        return new OpenAPI()
                .info(new Info()
                        .title("Secure Support Hub API")
                        .description("REST API for the Secure Support Hub — support request management with JWT auth + RBAC")
                        .version("v1")
                        .contact(new Contact()
                                .name("Secure Support Hub Team")
                                .email("admin@example.com"))
                        .license(new License().name("MIT")));
    }
}
