package showroomz.domain.notification.type;

/**
 * 토큰이 등록된 기기 종류.
 *
 * <p>발송 자체에는 필요 없다 — FCM은 토큰만으로 어디로 보낼지 안다. 그럼에도 남기는 이유는
 * iOS/Android가 알림 표시 규칙(배지·사운드·조용한 알림)이 달라 나중에 플랫폼별 페이로드를
 * 나눠야 할 때 토큰을 되짚어 분류할 방법이 없기 때문이다.
 *
 * <p>클라이언트가 값을 보내지 않으면 {@link #UNKNOWN}이다. 필수로 만들지 않는다 —
 * 이미 배포된 앱이 보내지 않는 값이고, 없다고 알림을 못 보낼 이유가 없다.
 */
public enum DevicePlatform {

    ANDROID,
    IOS,
    WEB,
    UNKNOWN;

    /** 대소문자·오타·null을 전부 {@link #UNKNOWN}으로 흘린다 — 로그인이 이 값 때문에 실패하면 안 된다 */
    public static DevicePlatform from(String value) {
        if (value == null || value.isBlank()) {
            return UNKNOWN;
        }
        try {
            return valueOf(value.trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            return UNKNOWN;
        }
    }
}
