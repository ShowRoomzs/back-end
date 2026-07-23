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
                        
                        - 유효기간: 7/1일까지
                        
                        - 샘플 토큰:
                        
                          USER   : eyJhbGciOiJIUzI1NiJ9.eyJzdWIiOiJ0ZXN0LXVzZXItMTIzIiwicm9sZSI6IlJPTEVfVVNFUiIsImV4cCI6MTc4OTkwMjIyNH0.tUYe7QOqQoFlin4US3O6QGSu9opEJ4vVGLHAqcQ6tvo
                          
                          SELLER : eyJhbGciOiJIUzI1NiJ9.eyJzdWIiOiJ0ZXN0LXVzZXItMTIzIiwicm9sZSI6IlJPTEVfU0VMTEVSIiwiZXhwIjoxNzg5OTAyMjI1fQ.bEiXbs2Lr02eVHX6VcXZkpRQBJrKANTu20UlGfu_UTc
                          
                          ADMIN  : eyJhbGciOiJIUzI1NiJ9.eyJzdWIiOiJ0ZXN0LXVzZXItMTIzIiwicm9sZSI6IlJPTEVfQURNSU4iLCJleHAiOjE3ODk5MDIyMjV9.uQmJ_lowuDD6U3vCwrc7tIUhqY0VgIGpoK8McJt7fH4
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
