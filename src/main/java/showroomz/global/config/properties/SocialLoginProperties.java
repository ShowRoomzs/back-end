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
    private Apple apple = new Apple();

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

    /**
     * 애플 — aud 허용 목록 + 탈퇴 시 토큰 철회(App Store 심사 5.1.1(v))용 키. 철회 키(team-id · key-id · private-key)가
     * 하나라도 비어 있으면 철회를 건너뛴다(기동 시 WARN) — 탈퇴 자체는 막지 않는다.
     */
    @Getter
    @Setter
    public static class Apple extends Audience {
        /** Apple Developer Team ID — client_secret JWT 의 iss. */
        private String teamId;
        /** Sign in with Apple 키 ID — client_secret JWT 헤더의 kid. */
        private String keyId;
        /** .p8 키 내용(PEM 헤더 포함 또는 본문만). 환경변수에서는 줄바꿈을 리터럴 「\n」으로 넣어도 된다. */
        private String privateKey;
        /** authorization code 를 받은 클라이언트 ID — 앱은 번들 ID. client_secret 의 sub 이자 client_id. */
        private String revokeClientId;

        public boolean revokeConfigured() {
            return notBlank(teamId) && notBlank(keyId) && notBlank(privateKey) && notBlank(revokeClientId);
        }
    }

    private static boolean notBlank(String value) {
        return value != null && !value.isBlank();
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
