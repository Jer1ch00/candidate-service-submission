package com.recruiting.candidates.config;

import com.recruiting.candidates.filter.ApiKeyFilter;
import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * OpenAPI 3 description of the API. With no frontend, this document is the contract for consumers. Served at
 * {@code /v3/api-docs} with Swagger UI at {@code /swagger-ui.html}, in the {@code dev} profile only.
 */
@Configuration(proxyBeanMethods = false)
public class OpenApiConfig {

    private static final String API_KEY_SCHEME = "apiKey";

    @Bean
    OpenAPI candidateServiceOpenApi() {
        return new OpenAPI()
                .info(new Info().title("Candidate Service API").version("v1").description("""
                                Create, view, update, delete, search and filter candidates, and move them through \
                                the hiring pipeline.

                                * Every request needs an `X-API-Key` header.
                                * Every error is an RFC 9457 problem document with a stable `code` and a `requestId`.
                                * List endpoints are always paginated (`page` from 0, `size` 1-100).
                                """))
                .components(new Components()
                        .addSecuritySchemes(
                                API_KEY_SCHEME,
                                new SecurityScheme()
                                        .type(SecurityScheme.Type.APIKEY)
                                        .in(SecurityScheme.In.HEADER)
                                        .name(ApiKeyFilter.HEADER)))
                .addSecurityItem(new SecurityRequirement().addList(API_KEY_SCHEME));
    }
}
