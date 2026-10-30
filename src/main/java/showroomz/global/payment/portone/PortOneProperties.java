package showroomz.global.payment.portone;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 포트원 V2 연동 설정(결제 계획서 6-2).
 *
 * <p>{@code enabled=false}면 {@link FakePaymentGateway}가 붙는다 — 자격 없는 로컬·CI. 그때는 나머지 값이 비어 있어도
 * 기동한다. 필수 값 검증은 {@link PortOneV2Gateway}가 만들어질 때(enabled=true) 한다(선행 수정 계획서 3-6).
 *
 * <p>채널키는 결제수단별 맵이다. 토스페이먼츠 허브형이라 지금은 네 값이 같은 키지만, 간편결제 하나를 직연동으로 떼거나
 * PG를 바꿔도 앱 스펙이 안 바뀌게 주문 생성 응답에 그 주문의 채널키를 실어 내린다(2-2). 값 {@code "-"}는 명시적으로 끈 채널이다.
 */
@Getter
@Setter
@ConfigurationProperties(prefix = "portone")
public class PortOneProperties {

    /** 채널을 명시적으로 끄는 값 — 주문서의 결제수단 목록에서 빠진다(5-2). */
    public static final String DISABLED_CHANNEL = "-";

    private boolean enabled = true;

    private String baseUrl = "https://api.portone.io";

    private String storeId;

    /** V2 API Secret — {@code Authorization: PortOne {secret}}. 로그에 남기지 않는다(6-3). */
    private String apiSecret;

    /** 콘솔 웹훅 설정의 시크릿(Standard Webhooks, {@code whsec_} 접두). */
    private String webhookSecret;

    private int connectTimeoutMillis = 3_000;

    private int readTimeoutMillis = 10_000;

    private ChannelKeys channelKeys = new ChannelKeys();

    private Identity identity = new Identity();

    @Getter
    @Setter
    public static class ChannelKeys {
        private String card;
        private String kakaopay;
        private String naverpay;
        private String tosspay;
        /** KCP 휴대폰 본인확인 채널(57 설계서 6-1) — 결제 채널과 다른 키. 결제 게이트웨이의 기동 검증은 이 값을 보지 않는다. */
        private String identity;
    }

    /**
     * 본인인증(57 설계서 6-1). {@code enabled=false} 면 결제가 켜져 있어도 {@code FakeIdentityGateway} 가 붙는다 — 채널 계약이 늦어도
     * 결제는 운영 자격으로 뜨게 하기 위한 분리 스위치다.
     */
    @Getter
    @Setter
    public static class Identity {
        private boolean enabled = true;
        /** 시작 → 완료 상한(분). 지나면 READY · VERIFIED 행을 만료로 읽는다. */
        private int ttlMinutes = 10;
        /** DI 중복 가입 차단 — 확정(0-5). 장애 때만 끄는 킬 스위치이고 끄더라도 DB UNIQUE 는 남는다. */
        private boolean duplicateBlock = true;
    }
}
