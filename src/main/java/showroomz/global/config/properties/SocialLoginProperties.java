package showroomz.global.config.properties;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * 소셜 로그인 공급자 설정 — 유저 앱 · 크리에이터 공용({@code SocialLoginService}).
 *
 * <p>aud 허용 목록({@code client-ids})은 <b>비어 있으면 aud 를 검증하지 않는다</b>(기동 시 WARN). 값을 모르는 채로 검증을 켜면
 * 운영 로그인이 한꺼번에 깨지므로, 클라이언트 ID 를 모두 채운 뒤에만 켜지게 한다. 앱 · 웹 · 크리에이터 스튜디오가 서로 다른
 * 클라이언트 ID 를 쓰므로 값은 여러 개다.
 */
@Getter
@Setter
@ConfigurationProperties(prefix = "social-login")
public class SocialLoginProperties {

    private Kakao kakao = new Kakao();
    private Audience google = new Audience();
    private Audience apple = new Audience();

    @Getter
    @Setter
    public static class Kakao {
        /** 카카오 REST API 키 — 인가 코드 교환(웹 로그인)의 client_id. 비어 있으면 인가 코드 로그인이 500 이다. */
        private String restApiKey;

        /** 카카오 콘솔에서 클라이언트 시크릿을 켰을 때만 채운다. */
        private String clientSecret;

        /**
         * 인가 코드 교환에 쓸 수 있는 redirect_uri — 카카오 콘솔 등록값과 같아야 한다. 목록 밖의 값은 카카오로 보내기 전에 400 이다
         * (임의 redirect_uri 로 받은 코드를 우리 키로 교환해 주지 않는다).
         */
        private List<String> allowedRedirectUris = new ArrayList<>();

        public List<String> allowedRedirectUris() {
            return clean(allowedRedirectUris);
        }
    }

    @Getter
    @Setter
    public static class Audience {
        /** 허용할 aud(클라이언트 ID) 목록. 비어 있으면 aud 를 검증하지 않는다. */
        private List<String> clientIds = new ArrayList<>();

        public List<String> allowedAudiences() {
            return clean(clientIds);
        }
    }

    private static List<String> clean(List<String> values) {
        if (values == null) {
            return List.of();
        }
        return values.stream()
                .filter(Objects::nonNull)
                .map(String::trim)
                .filter(v -> !v.isEmpty())
                .toList();
    }
}
