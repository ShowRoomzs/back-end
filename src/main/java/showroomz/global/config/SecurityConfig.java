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
import org.springframework.http.HttpMethod;

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
            "/v1/creator/auth/social/login",
            "/v1/creator/auth/complete-registration",
            "/v1/creator/auth/registration-info",
            "/v1/creator/auth/check-showroom-name",
            "/v1/seller/auth/signup", "/v1/seller/auth/login",
            "/v1/seller/auth/complete-registration",
            "/v1/seller/auth/refresh",
            "/v1/admin/auth/login",
            "/v1/admin/auth/refresh",

            // 중복 확인 (인증 불필요)
            "/v1/seller/auth/check-email",
            "/v1/seller/auth/check-business-registration-number",
            "/v1/user/check-nickname",
            "/v1/seller/markets/check-name",

            // 공용 공개 이미지 업로드 (비로그인, type whitelist)
            "/v1/common/images",

            // 공용 상품 목록 조회 (비회원 허용)
            "/v1/common/products",
            "/v1/common/products/**",

            // 공용 카테고리 조회 (비회원 허용)
            "/v1/common/categories/**",

            // 공용 필터 조회 (비회원 허용)
            "/v1/common/filters/**",

            // 공용 API 전체 허용
            "/v1/common/**",

            // 쇼룸 조회 API (구 샵 조회 API) — 소비자에게 조회되는 것은 마켓이 아니라 쇼룸뿐이다.
            // 상세는 ID를 숫자로 못 박는다. `{showroomId}`로 두면 로그인이 필요한
            // `/v1/user/showrooms/following`까지 함께 열려 버린다.
            "/v1/user/showrooms",
            "/v1/user/showrooms/{showroomId:[0-9]+}",

            // 검색 자동완성 (비로그인 허용)
            "/v1/user/search/autocomplete",

            // C14 쇼룸 검색 — 최근 검색만 로그인이 필요하고, 검색 자체와 추천 목록은 비로그인도 볼 수 있다.
            "/v1/user/search/showrooms",
            "/v1/user/search/showrooms/active",

            // FAQ 조회 API
            "/v1/user/faqs",

            // 쇼룸 게시글 조회 API — 실제 경로는 `showrooms`(복수)다. 단수로 적혀 있던 동안
            // 비로그인 열람이 조용히 막혀 있었다(C4 §비로그인 — 열람은 자유, 팔로우·♥만 로그인).
            "/v1/user/showrooms/*/posts", "/v1/user/showrooms/posts/*",

            // 쇼룸 방문 기록 (§22-4) — 비로그인 방문도 쇼룸 도달 지표에 포함되므로 인증을 요구하지 않는다.
            // 토큰이 실려 오면 필터가 인증을 채워 주므로, 로그인 방문은 사용자 기준으로 집계된다.
            "/v1/user/showrooms/*/visits",

            // 게시물 노출 적재 (§24-7) — 방문 기록과 같은 이유로 비로그인 조회도 노출에 포함된다.
            // 로그인 조회만 연령·성별 표본이 되고, 비로그인은 "미확인"으로 분류된다.
            "/v1/user/posts/impressions",
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

                // SELLER auth - logout, withdraw, images는 ADMIN과 SELLER, CREATOR 모두 접근 가능
                .requestMatchers("/v1/seller/auth/logout", "/v1/seller/auth/withdraw", "/v1/seller/images")
                    .hasAnyAuthority(RoleType.ADMIN.getCode(), RoleType.SELLER.getCode(), RoleType.CREATOR.getCode())

                // SELLER 권한
                .requestMatchers("/v1/seller/**").hasAnyAuthority(RoleType.SELLER.getCode(), RoleType.CREATOR.getCode())

                // 크리에이터 권한 신청 (로그인한 일반 유저)
                .requestMatchers("/v1/creator/application").hasAnyAuthority(RoleType.USER.getCode())

                // CREATOR 권한
                .requestMatchers("/v1/creator/**").hasAnyAuthority(RoleType.CREATOR.getCode())

                // USER 권한
                .requestMatchers("/v1/user/**").hasAnyAuthority(RoleType.USER.getCode(), RoleType.CREATOR.getCode())
                
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
