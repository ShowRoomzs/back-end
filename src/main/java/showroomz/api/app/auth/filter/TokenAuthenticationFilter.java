package showroomz.api.app.auth.filter;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

import showroomz.api.app.auth.token.AuthToken;
import showroomz.api.app.auth.token.AuthTokenProvider;
import showroomz.global.utils.HeaderUtil;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import java.io.IOException;

@Slf4j
@RequiredArgsConstructor
public class TokenAuthenticationFilter extends OncePerRequestFilter {
    private final AuthTokenProvider tokenProvider;

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain)  throws ServletException, IOException {

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
