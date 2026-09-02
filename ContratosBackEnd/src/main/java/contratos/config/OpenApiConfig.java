package contratos.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenApiConfig {
    private static final String SECURITY_SCHEME = "bearerAuth";

    @Bean
    OpenAPI contratosOpenApi() {
        return new OpenAPI()
                .info(new Info()
                        .title("API de Controle de Contratos CISBAF")
                        .description("API para gestão, fiscalização e renovação de contratos")
                        .version("v1"))
                .components(new Components()
                        .addSecuritySchemes(
                                SECURITY_SCHEME,
                                new SecurityScheme()
                                        .name(SECURITY_SCHEME)
                                        .type(SecurityScheme.Type.HTTP)
                                        .scheme("bearer")
                                        .bearerFormat("JWT")
                        ))
                .addSecurityItem(
                        new SecurityRequirement().addList(SECURITY_SCHEME)
                );
    }
}
