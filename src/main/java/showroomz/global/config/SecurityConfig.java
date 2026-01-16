package showroomz.global.config;

import lombok.RequiredArgsConstructor;
import showroomz.api.app.auth.entity.RoleType;
import showroomz.api.app.auth.exception.RestAuthenticationEntryPoint;
import showroomz.api.app.auth.filter.TokenAuthenticationFilter;
import showroomz.api.app.auth.handler.TokenAccessDeniedHandler;
import showroomz.api.app.auth.service.CustomUserDetailsService;
import showroomz.api.app.auth.token.AuthTokenProvider;
import showroomz.global.config.properties.CorsProperties;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.AuthenticationProvider;
import org.springframework.security.authentication.dao.DaoAuthenticationProvider;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsUtils;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.util.Arrays;

@Configuration
@RequiredArgsConstructor
public class SecurityConfig {

    private final CorsProperties corsProperties;
    private final AuthTokenProvider tokenProvider;
    private final CustomUserDetailsService userDetailsService;
    private final TokenAccessDeniedHandler tokenAccessDeniedHandler;
    
    private static final String[] AUTH_WHITELIST = {
            "/", "/error", "/test/**",  // 기본
            "/swagger-ui.html", "/swagger-ui/**", "/v3/api-docs/**", "/api-docs/**", // Swagger

            //auth 관련
            "/v1/user/auth/social/login", 
            "/v1/user/auth/social/signup", 
            "/v1/user/auth/refresh",
            "/v1/user/auth/local/signup", "/v1/user/auth/local/login",
            "/v1/seller/auth/signup", "/v1/seller/auth/login",
            "/v1/seller/auth/refresh",
            "/v1/admin/auth/login",
            "/v1/admin/auth/refresh",

            // 중복 확인 (인증 불필요)
            "/v1/seller/auth/check-email",
            "/v1/user/check-nickname",
            "/v1/seller/markets/check-name",
            
            // 마켓 조회 (인증 불필요 - 비로그인 가능)
            "/v1/user/markets/*",

            // 공용 상품 목록 조회 (비회원 허용)
            "/v1/common/products",

            // 공용 카테고리 조회 (비회원 허용)
            "/v1/common/categories/**"
    };
    /*
     * SecurityFilterChain 설정 (Spring Security 3.x 최신 방식)
     */
    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
            .sessionManagement(session -> session
                .sessionCreationPolicy(SessionCreationPolicy.STATELESS)
            )
            .csrf(AbstractHttpConfigurer::disable) // 람다식 간소화
            .cors(cors -> cors.configurationSource(corsConfigurationSource()))
            .formLogin(AbstractHttpConfigurer::disable)
            .httpBasic(AbstractHttpConfigurer::disable)
            .exceptionHandling(exceptions -> exceptions
                .authenticationEntryPoint(new RestAuthenticationEntryPoint())
                .accessDeniedHandler(tokenAccessDeniedHandler)
            )
            .authorizeHttpRequests(auth -> auth
                .requestMatchers(CorsUtils::isPreFlightRequest).permitAll()
                .requestMatchers(AUTH_WHITELIST).permitAll()       
                
                // ADMIN 전용
                .requestMatchers("/v1/admin/**").hasAnyAuthority(RoleType.ADMIN.getCode())

                // SELLER auth - logout, withdraw, images는 ADMIN과 SELLER 모두 접근 가능
                .requestMatchers("/v1/seller/auth/logout", "/v1/seller/auth/withdraw", "/v1/seller/images")
                    .hasAnyAuthority(RoleType.ADMIN.getCode(), RoleType.SELLER.getCode())

                // SELLER 권한
                .requestMatchers("/v1/seller/**").hasAnyAuthority(RoleType.SELLER.getCode())

                // USER 권한
                .requestMatchers("/v1/user/**").hasAnyAuthority(RoleType.USER.getCode())
                
                .anyRequest().authenticated()
            );

        http.addFilterBefore(tokenAuthenticationFilter(), UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }
    /*
     * AuthenticationManager 설정
     */
    @Bean
    public AuthenticationManager authenticationManager(AuthenticationConfiguration authenticationConfiguration) throws Exception {
        return authenticationConfiguration.getAuthenticationManager();
    }

    /*
     * AuthenticationProvider 설정
     */
    @Bean
    public AuthenticationProvider authenticationProvider() {
        DaoAuthenticationProvider authProvider = new DaoAuthenticationProvider(userDetailsService);
        authProvider.setPasswordEncoder(passwordEncoder());
        return authProvider;
    }

    /*
     * security 설정 시, 사용할 인코더 설정
     */
    @Bean
    public BCryptPasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    /*
     * 토큰 필터 설정
     */
    @Bean
    public TokenAuthenticationFilter tokenAuthenticationFilter() {
        return new TokenAuthenticationFilter(tokenProvider);
    }

    /*
     * Cors 설정
     */
    /*
     * ✅ CORS 설정 소스 (Spring Security용)
     */
    @Bean
    public UrlBasedCorsConfigurationSource corsConfigurationSource() {
        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        CorsConfiguration config = new CorsConfiguration();

        config.setAllowedHeaders(Arrays.asList(corsProperties.getAllowedHeaders().split(",")));
        config.setAllowedMethods(Arrays.asList(corsProperties.getAllowedMethods().split(",")));
        config.setAllowedOrigins(Arrays.asList(corsProperties.getAllowedOrigins().split(",")));
        config.setAllowCredentials(true);
        config.setMaxAge(corsProperties.getMaxAge());

        source.registerCorsConfiguration("/**", config);
        return source;
    }

}
