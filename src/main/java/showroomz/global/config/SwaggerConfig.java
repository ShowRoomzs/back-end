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
                        - 유효기간: 3/8일까지
                        - 샘플 토큰:
                          USER  : eyJhbGciOiJIUzI1NiJ9.eyJzdWIiOiJ0ZXN0LXVzZXItMTIzIiwicm9sZSI6IlJPTEVfVVNFUiIsImV4cCI6MTc3MzAzNjgzMH0.gpYsAkzzuPkXPb-JuwvBY_5OkkMzh4-4OHBCxxZYhrk
                          SELLER: eyJhbGciOiJIUzI1NiJ9.eyJzdWIiOiJ0ZXN0LXVzZXItMTIzIiwicm9sZSI6IlJPTEVfU0VMTEVSIiwiZXhwIjoxNzczMDM2ODMwfQ.E6XYiAqVRE5f-P0oSMO08o_vPVj9-E9bUSZLboRN4M8
                          ADMIN : eyJhbGciOiJIUzI1NiJ9.eyJzdWIiOiJ0ZXN0LXVzZXItMTIzIiwicm9sZSI6IlJPTEVfQURNSU4iLCJleHAiOjE3NzMwMzY4MzB9.vP2P5Rf6mr3_vC3upehi6uKf8H6rvFbOg7VDt2hn6S4
                        """,
                version = "v1"
        ),
        servers = {
                @Server(url = "https://api.showroomz.shop", description = "배포 서버"),
                @Server(url = "http://localhost:8080", description = "로컬 서버")
        }
)
@Configuration
public class SwaggerConfig {

    private static final String AUTHORIZATION_HEADER = "Authorization";
    private static final String BEARER_TOKEN_PREFIX = "Bearer";

    @Bean
    @Profile("!prod") // 운영 환경에서 Swagger 비활성화
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
