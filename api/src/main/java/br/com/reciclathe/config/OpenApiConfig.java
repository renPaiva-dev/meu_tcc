package br.com.reciclathe.config;

import io.swagger.v3.oas.annotations.OpenAPIDefinition;
import io.swagger.v3.oas.annotations.enums.SecuritySchemeType;
import io.swagger.v3.oas.annotations.info.Info;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.security.SecurityScheme;

import org.springframework.context.annotation.Configuration;

/** Documentação da API do app em /swagger-ui.html (desligável com DOCS_API=false). */
@Configuration
@OpenAPIDefinition(
        info = @Info(title = "ReciclaTHE API", version = "v1",
                description = "API do app ReciclaTHE. Erros seguem RFC 9457 (application/problem+json)."),
        security = @SecurityRequirement(name = "jwt"))
@SecurityScheme(name = "jwt", type = SecuritySchemeType.HTTP, scheme = "bearer", bearerFormat = "JWT")
public class OpenApiConfig {
}
