package com.mirrorsoul.mirrorsoul_api.common.swagger;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import io.swagger.v3.oas.models.servers.Server;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class SwaggerConfig {

    private static final String BEARER_AUTH_SCHEME = "bearerAuth";

    @Bean
    public OpenAPI swagger() {
        Info info = new Info()
                .title("Mirror Soul API Swagger")
                .description("Mirror Soul service API documentation.")
                .version("0.0.1");

        SecurityRequirement securityRequirement = new SecurityRequirement().addList(BEARER_AUTH_SCHEME);

        Components components = new Components()
                .addSecuritySchemes(BEARER_AUTH_SCHEME, new SecurityScheme()
                        .name(BEARER_AUTH_SCHEME)
                        .type(SecurityScheme.Type.HTTP)
                        .scheme("bearer")
                        .bearerFormat("JWT"));

        return new OpenAPI()
                .info(info)
                .addServersItem(new Server().url("/"))
                .components(components);
    }
}
