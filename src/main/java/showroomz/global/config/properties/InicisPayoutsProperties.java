package showroomz.global.config.properties;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.net.URI;
import java.time.LocalTime;

/**
 * KG이니시스 지급대행(신버전 AML) 설정(60_KG이니시스_지급대행_전환_계획서.md 7절). 결제(포트원)와 키 · base URL 을 공유하지 않는다.
 * API 키는 로그에 남기지 않는다(D-11).
 */
@Getter
@Setter
@ConfigurationProperties(prefix = "inicis.payouts")
public class InicisPayoutsProperties {

    /** 운영 API 호스트 — {@code PG} 는 이 호스트만, {@code PG_TEST} 는 이 호스트를 쓸 수 없다(키 접두로 환경을 가를 수 없어 URL 로 가른다 · D-10). */
    public static final String PRODUCTION_HOST = "apiwi.inicis.com";
    /** 가맹점 MID 길이 상한(1-3 {@code idMerchant} 10). */
    public static final int MERCHANT_ID_MAX = 10;

    private String baseUrl = "https://" + PRODUCTION_HOST;

    /** 지급대행 MID — 결제 MID 와 다를 수 있다(Q1). */
    private String merchantId;

    /** {@code X-API-KEY} — 가맹점관리자 「부가서비스 연동 KEY 정보 > 지급대행 서비스」. 로그 금지. */
    private String apiKey;

    private int connectTimeoutMillis = 3_000;

    private int readTimeoutMillis = 10_000;

    /** {@code PG_TEST} 의 {@code idMall} 접두(D-3) — 운영은 접두 없음. */
    private String subMallRefPrefixTest = "t";

    /** 보내는 {@code dtPay} 의 뜻(Q2 · D-2) — 돈이 나가는 날 / 업로드 기준일(다음 날 지급). */
    private DtPayBasis dtPayBasis = DtPayBasis.PAYMENT_DATE;

    /** 익일지급 수정 · 재업로드 마감(서비스 안내 「익일 지급건 15시 전」). */
    private LocalTime uploadCutoff = LocalTime.of(15, 0);

    /** 당일지급({@code clService=2}) — 별도 계약 뒤에만. 이 구현은 아직 쓰지 않는다(D-2′). */
    private boolean sameDayEnabled = false;

    private LocalTime sameDayCutoff = LocalTime.of(13, 0);

    /** 7일 1천만 원(AML 고객확인 기준) — 안내만 / 선보류(Q8 답 전까지 ALERT · D-6). */
    private AmlPrecheck amlPrecheck = AmlPrecheck.ALERT;

    /** 14:00 재검사에도 잔액이 모자라면 큰 묶음부터 남기고 나머지를 지운다(D-9 · Q1 답 전 보수적 기본). */
    private boolean trimOnShortfall = true;

    /** 기동 시 {@code paycheck} 1회(인증 · 계약). {@code PG_TEST}(스테이징)는 조회가 막혀 있어 건너뛴다(1-8). */
    private boolean startupCheck = true;

    /**
     * 서브몰 등록({@code upsert})이 예금주 · 계좌 실명을 대조하는가(Q7 · D-5). true 로 확정되기 전에는 {@code PG} 로 기동하지 않는다 — 은행 이체는
     * 예금주를 대조하지 않아 오입금이 그대로 나간다. 별도 예금주 조회를 붙이면 그때 함께 연다.
     */
    private boolean holderVerifiedByUpsert = false;

    private Test test = new Test();

    public enum DtPayBasis {
        /** {@code dtPay} = 돈이 나가는 날(D) — 기본 가정. */
        PAYMENT_DATE,
        /** {@code dtPay} = 업로드 기준일(X · 다음 날 지급). 지시 시각은 그대로 두고 보내는 값만 바꾼다. */
        UPLOAD_DATE
    }

    public enum AmlPrecheck {
        /** 지시는 하고 수취자 · 운영자에게 「고객확인 대상이 될 수 있음」 안내. */
        ALERT,
        /** 7일 누적 + 이번 행 ≥ 기준이면 먼저 보류({@code KYC_REQUIRED}). */
        BLOCK
    }

    @Getter
    @Setter
    public static class Test {
        /**
         * {@code PG_TEST} 전용 — 스테이징은 송금 결과를 조회할 수 없어(1-8) 지급일이 지난 묶음을 PAID 로 돌려준다. 개발 서버에서 「지급 완료 → SETTLED →
         * 영수증」을 끝까지 돌리기 위함이다. {@code PG} 에서 true 면 기동 실패.
         */
        private boolean assumePaid = false;
    }

    /** base URL 의 호스트가 운영 호스트인가. 해석하지 못하면 false. */
    public boolean isProductionHost() {
        try {
            String host = URI.create(baseUrl == null ? "" : baseUrl.trim()).getHost();
            return PRODUCTION_HOST.equalsIgnoreCase(host);
        } catch (IllegalArgumentException e) {
            return false;
        }
    }
}
