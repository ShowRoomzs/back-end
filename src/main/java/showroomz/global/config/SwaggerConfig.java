package showroomz.global.config;

import io.swagger.v3.oas.annotations.OpenAPIDefinition;
import io.swagger.v3.oas.annotations.info.Info;
import io.swagger.v3.oas.annotations.servers.Server;
import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;

@OpenAPIDefinition(
        info = @Info(
                title = "Showroomz API 명세서",
                description = """
                        테스트용 JWT
                        
                        - 샘플 토큰:
                        
                          USER   : eyJhbGciOiJIUzI1NiJ9.eyJzdWIiOiIzd1p1bnBWYUoycmttSTNhM0FOa0NTRmlNNjJKWWxNS2U0MnpUbkZrQkxrIiwicm9sZSI6IlJPTEVfVVNFUiIsInBrIjoxMSwiZXhwIjoxODE3NjQwNDk2fQ.j2tp_uERRtGWY1pICVrKQdZAlgxM5g6SHTRAVI8ddq4
                          
                          SELLER : eyJhbGciOiJIUzI1NiJ9.eyJzdWIiOiJzZWxsZXJAZXhhbXBsZS5jb20iLCJyb2xlIjoiUk9MRV9TRUxMRVIiLCJwayI6OCwiZXhwIjoxODE3NjQwMzg3fQ.wpS-Kal5V0HGoo13YTL2A1T8OCqaZUttQjkLiqM6uSQ
                          
                          ADMIN  : eyJhbGciOiJIUzI1NiJ9.eyJzdWIiOiJzdXBlciIsInJvbGUiOiJST0xFX0FETUlOIiwicGsiOjEsImV4cCI6MTgxNzY0MDQzNn0.kgsX8tTiCjME0pjagc92brdZq2B4LDqg7OTZNQc6x6U
                        """,
                version = "v1"
        ),
        servers = {
                @Server(url = "/", description = "Default Server")
        }
)
@Configuration
public class SwaggerConfig {

    private static final String AUTHORIZATION_HEADER = "Authorization";
    private static final String BEARER_TOKEN_PREFIX = "Bearer";

    @Bean
    public OpenAPI openAPI() {
        SecurityRequirement securityRequirement = new SecurityRequirement().addList(AUTHORIZATION_HEADER);
        Components components = new Components()
                .addSecuritySchemes(AUTHORIZATION_HEADER, new SecurityScheme()
                        .name(AUTHORIZATION_HEADER)
                        .type(SecurityScheme.Type.HTTP)
                        .scheme("bearer")
                        .bearerFormat("JWT"));

        return new OpenAPI()
                .addSecurityItem(securityRequirement)
                .components(components);
    }
}
