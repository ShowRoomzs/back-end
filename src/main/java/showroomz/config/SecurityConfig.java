package showroomz.config;

import lombok.RequiredArgsConstructor;

import org.springframework.web.filter.CorsFilter;

import showroomz.config.properties.CorsProperties;
import showroomz.oauthlogin.oauth.entity.RoleType;
import showroomz.oauthlogin.oauth.exception.RestAuthenticationEntryPoint;
import showroomz.oauthlogin.oauth.filter.TokenAuthenticationFilter;

import showroomz.oauthlogin.oauth.handler.TokenAccessDeniedHandler;
import showroomz.oauthlogin.oauth.service.CustomUserDetailsService;
import showroomz.oauthlogin.oauth.token.AuthTokenProvider;

import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.AuthenticationProvider;
import org.springframework.security.authentication.dao.DaoAuthenticationProvider;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
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
            "/swagger-ui/**", "/swagger-ui.html", "/swagger-ui/index.html",
            "/api-docs", "/swagger-ui-custom.html", "/payment/**",
            "/v3/api-docs/**", "/api-docs/**", 
            "/v1/auth/social/login", "/v1/auth/register", "/v1/auth/refresh",  // 토큰 갱신 (인증 불필요)
            "/v1/users/check-nickname",  // 닉네임 중복 확인 (인증 불필요)
            "/error"  // 에러 페이지 접근 허용
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
            .csrf(csrf -> csrf.disable())
            .cors(cors -> cors.configurationSource(corsConfigurationSource()))
            .formLogin(form -> form.disable())
            .httpBasic(httpBasic -> httpBasic.disable())
            .exceptionHandling(exceptions -> exceptions
                .authenticationEntryPoint(new RestAuthenticationEntryPoint())
                .accessDeniedHandler(tokenAccessDeniedHandler)
            )
            .authorizeHttpRequests(auth -> auth
                .requestMatchers(CorsUtils::isPreFlightRequest).permitAll()
                .requestMatchers(AUTH_WHITELIST).permitAll()
                .requestMatchers("/api/*/admin/**").hasAnyAuthority(RoleType.ADMIN.getCode())
                .requestMatchers("/api/**").hasAnyAuthority(RoleType.USER.getCode())
                .anyRequest().authenticated()
            );
            
            // .oauth2Login(...) 블록 전체 삭제

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
