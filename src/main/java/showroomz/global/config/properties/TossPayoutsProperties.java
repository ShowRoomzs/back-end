package showroomz.global.config.properties;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.LocalTime;

/**
 * 토스페이먼츠 지급대행(Payouts v2) 설정(44_토스페이먼츠_지급대행_연동_BE_설계서.md 6-3). 결제(포트원 허브형)와 키 · base URL 을 공유하지
 * 않는다 — 지급대행은 토스 직접 API 다(2-2). 시크릿 키 · 보안 키는 로그에 남기지 않는다.
 */
@Getter
@Setter
@ConfigurationProperties(prefix = "toss.payouts")
public class TossPayoutsProperties {

    public static final String TEST_KEY_PREFIX = "test_sk_";
    public static final String LIVE_KEY_PREFIX = "live_sk_";
    /** 이체 적요 상한 — 토스 {@code transactionDescription} ≤7자(1-4). */
    public static final int TRANSACTION_DESCRIPTION_MAX = 7;

    private String baseUrl = "https://api.tosspayments.com";

    /** Basic 인증 시크릿 키 — {@code test_sk_…}(PG_TEST) / {@code live_sk_…}(PG). 접두가 모드와 다르면 기동 실패(6-4). */
    private String secretKey;

    /** 개발자센터 「API 개별 키 > 보안 키」 — 64자 hex. JWE 암·복호화와 웹훅 서명 검증 둘 다 이 키다. */
    private String securityKey;

    private int connectTimeoutMillis = 3_000;

    private int readTimeoutMillis = 10_000;

    /** 수취자 통장에 찍히는 이체 적요 — ≤7자(기획 L-3). */
    private String transactionDescription = "쇼룸즈정산";

    /** PG_TEST 의 {@code refSellerId · refPayoutId} 접두 — 토스는 키로 데이터가 갈리지만 콘솔에서 눈으로 가르기 위해 둔다(3-4). */
    private String partnerRefPrefixTest = "t-";

    /** EXPRESS(당일 지급) 요청 가능 창 — 영업일 [start, end). 밖이면 SCHEDULED(다음 영업일)(0-4 · Q3). */
    private LocalTime expressWindowStart = LocalTime.of(8, 0);

    private LocalTime expressWindowEnd = LocalTime.of(15, 0);

    /** 기동 시 {@code GET /v2/balances} 1회 — 인증 · 계약 확인. 토스 장애로 앱이 못 뜨는 것이 싫으면 끈다. */
    private boolean startupCheck = true;

    /** 결과 모름 행(지급 id 없음)이 N영업일 뒤에도 토스 목록에 없으면 「지급 미생성」으로 닫는다(5-4 ②). */
    private int notFoundGraceBusinessDays = 1;

    private Test test = new Test();

    public boolean isTestKey() {
        return secretKey != null && secretKey.startsWith(TEST_KEY_PREFIX);
    }

    public boolean isLiveKey() {
        return secretKey != null && secretKey.startsWith(LIVE_KEY_PREFIX);
    }

    @Getter
    @Setter
    public static class Test {
        /**
         * PG_TEST 전용 — 테스트 환경은 법인 셀러만 등록되므로(0-10) 비법인 수취자를 이 법인 사업자번호로 대체 등록한다. 비우면 비법인 수취자는
         * 등록 실패로 보류된다. PG 에서 값이 있으면 기동 실패.
         */
        private String corporateOverrideBrn;

        public boolean hasCorporateOverride() {
            return corporateOverrideBrn != null && !corporateOverrideBrn.isBlank();
        }
    }
}
