package showroomz.api.app.auth.token;

import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseCookie;
import org.springframework.stereotype.Component;
import showroomz.global.config.properties.AppProperties;

import java.time.Duration;
import java.util.Optional;

/**
 * 유저 웹 리프레시 토큰 쿠키(httpOnly · Secure).
 *
 * <p>앱은 지금처럼 리프레시 토큰을 바디로 주고받는다. 웹은 바디로 받으면 localStorage 에 둘 수밖에 없어 XSS 한 번에 장기 토큰이 샌다 —
 * 그래서 로그인 · 가입 요청이 {@code platform=WEB} 이면 리프레시 토큰을 쿠키로만 내리고 바디에서는 뺀다. 재발급 · 로그아웃은 바디가
 * 비어 있으면 쿠키를 읽는다(바디가 오면 바디 우선 — 앱 동작 불변).
 */
@Component
@RequiredArgsConstructor
public class WebRefreshTokenCookie {

    public static final String WEB_PLATFORM = "WEB";

    private final AppProperties appProperties;

    public boolean isWeb(String platform) {
        return platform != null && WEB_PLATFORM.equalsIgnoreCase(platform.trim());
    }

    /** {@code Set-Cookie} 값 — 수명은 리프레시 토큰과 같다. */
    public String issue(String refreshToken) {
        return base(refreshToken)
                .maxAge(Duration.ofMillis(appProperties.getAuth().getRefreshTokenExpiry()))
                .build()
                .toString();
    }

    /** 쿠키를 지우는 {@code Set-Cookie} 값. */
    public String expire() {
        return base("").maxAge(Duration.ZERO).build().toString();
    }

    public Optional<String> read(HttpServletRequest request) {
        Cookie[] cookies = request.getCookies();
        if (cookies == null) {
            return Optional.empty();
        }
        String name = appProperties.getWebRefreshCookie().getName();
        for (Cookie cookie : cookies) {
            if (name.equals(cookie.getName()) && cookie.getValue() != null && !cookie.getValue().isBlank()) {
                return Optional.of(cookie.getValue());
            }
        }
        return Optional.empty();
    }

    private ResponseCookie.ResponseCookieBuilder base(String value) {
        AppProperties.WebRefreshCookie config = appProperties.getWebRefreshCookie();
        return ResponseCookie.from(config.getName(), value)
                .httpOnly(true)
                .secure(config.isSecure())
                .sameSite(config.getSameSite())
                .path(config.getPath());
    }
}
