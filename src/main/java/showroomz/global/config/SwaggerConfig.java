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
                        
                          USER   : eyJhbGciOiJIUzI1NiJ9.eyJzdWIiOiJ0ZXN0LXVzZXItMTIzIiwicm9sZSI6IlJPTEVfVVNFUiIsImV4cCI6MTc4Mjc5Njg0MX0.uSJgR2ZOFHWIPrZ_PQdCdLsSvhKZ1H-UVzpaKYJURuE
                          
                          SELLER : eyJhbGciOiJIUzI1NiJ9.eyJzdWIiOiJ0ZXN0LXVzZXItMTIzIiwicm9sZSI6IlJPTEVfU0VMTEVSIiwiZXhwIjoxNzgyNzk2ODQyfQ.Y5B0BGRzLEIwiBCiem4-8dAcaKeDm9RF0ifCFOAq4vY
                          
                          ADMIN  : eyJhbGciOiJIUzI1NiJ9.eyJzdWIiOiJ0ZXN0LXVzZXItMTIzIiwicm9sZSI6IlJPTEVfQURNSU4iLCJleHAiOjE3ODI3OTY4NDJ9.sCtz_DWMz_4wyUQmO0jfRBmktA62zXqA2wRZ0Edi5pc
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
