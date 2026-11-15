package showroomz.global.config.properties;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

@Getter
@ConfigurationProperties(prefix = "app")
public class AppProperties {

    private final Auth auth = new Auth();
    private final NaverOAuth2 naverOAuth2 = new NaverOAuth2();
    private final WebRefreshCookie webRefreshCookie = new WebRefreshCookie();

    /**
     * 유저 웹 리프레시 토큰 쿠키 — 로그인 요청의 {@code platform=WEB} 일 때만 내린다. 앱은 지금처럼 바디로 주고받는다.
     *
     * <p>소비자 웹(showroomz.co.kr)과 API(api.showroomz.co.kr)는 같은 사이트라 {@code SameSite=Lax} 로 충분하다.
     * 다른 사이트(예: localhost 개발 서버)에서 쿠키를 쓰려면 {@code None} 으로 바꾼다({@code Secure} 필수).
     */
    @Getter
    @Setter
    @NoArgsConstructor
    public static class WebRefreshCookie {
        private String name = "showroomz_rt";
        private String path = "/v1/user/auth";
        private String sameSite = "Lax";
        private boolean secure = true;
    }

    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Auth {
        private String tokenSecret;
        private long tokenExpiry;
        private long refreshTokenExpiry;
    }

    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    public static class NaverOAuth2 {
        private String clientId;
        private String clientSecret;
        private String redirectUri;
    }
}