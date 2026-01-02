package showroomz.auth.filter;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

import showroomz.auth.token.AuthToken;
import showroomz.auth.token.AuthTokenProvider;
import showroomz.utils.HeaderUtil;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import java.io.IOException;

@Slf4j
@RequiredArgsConstructor
public class TokenAuthenticationFilter extends OncePerRequestFilter {
    private final AuthTokenProvider tokenProvider;

    private static final String[] AUTH_WHITELIST = {
            "/",
            "/swagger-ui/**", "/swagger-ui.html", "/swagger-ui/index.html",
            "/api-docs", "/swagger-ui-custom.html", "/payment/**",
            "/v3/api-docs/**", "/api-docs/**",
            "/v1/auth/social/login", "/v1/auth/register", "/v1/auth/refresh",
            "/v1/auth/local/signup", "/v1/auth/local/login",
            "/v1/admin/signup", "/v1/admin/login",
            "/v1/admin/check-email", "/v1/admin/check-market-name",
            "/v1/users/check-nickname",
            "/error"
    };

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain)  throws ServletException, IOException {

        String requestPath = request.getRequestURI();
        
        // Whitelist 경로는 필터 건너뛰기
        for (String path : AUTH_WHITELIST) {
            if (path.endsWith("/**")) {
                String basePath = path.substring(0, path.length() - 3);
                if (requestPath.startsWith(basePath)) {
                    filterChain.doFilter(request, response);
                    return;
                }
            } else if (requestPath.equals(path)) {
                filterChain.doFilter(request, response);
                return;
            }
        }

        String tokenStr = HeaderUtil.getAccessToken(request);
        
        // 토큰이 없으면 필터 통과
        if (tokenStr == null || tokenStr.isEmpty()) {
            filterChain.doFilter(request, response);
            return;
        }
        
        AuthToken token = tokenProvider.convertAuthToken(tokenStr);

        // 토큰이 유효하고 role 클레임이 있는 경우만 인증 처리
        if (token.validate()) {
            try {
                Authentication authentication = tokenProvider.getAuthentication(token);
                SecurityContextHolder.getContext().setAuthentication(authentication);
            } catch (Exception e) {
                // role이 없는 토큰(예: register 토큰)은 인증 처리하지 않고 통과
                log.debug("Token authentication skipped: {}", e.getMessage());
            }
        }

        filterChain.doFilter(request, response);
    }

}
