package showroomz.domain.settlement;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;
import showroomz.domain.settlement.port.SettlementPartnerGateway;
import showroomz.domain.settlement.port.SettlementPayoutGateway;
import showroomz.global.config.SettlementPayoutGatewayConfig;
import showroomz.global.config.properties.SettlementProperties;
import showroomz.global.config.properties.SettlementProperties.PayoutMode;
import showroomz.global.config.properties.TossPayoutsProperties;
import showroomz.global.payment.settlement.NoopSettlementPartnerGateway;
import showroomz.global.payment.settlement.SimulatedSettlementPayoutGateway;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 지급 모드 기동 검증(44 어드민 설계서 0-5 · 토스 설계서 6-4) — 설정이 틀렸으면 컨텍스트가 뜨지 않는다. 토스 호출은 하지 않는다
 * (설정 검증이 네트워크 앞에서 끝난다).
 */
@DisplayName("정산 지급 모드 — SIMULATED · PG_TEST · PG 기동 검증 · 런칭 게이트 · 토스 키")
class SettlementPayoutGatewayConfigTest {

    private static final String KEY_HEX = "0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef";

    private final SettlementPayoutGatewayConfig config = new SettlementPayoutGatewayConfig();

    @Test
    @DisplayName("SIMULATED · 비운영 프로필 → 시뮬레이터 · 셀러 포트는 Noop(연결 기록 없음)")
    void simulatedOutsideProd() {
        MockEnvironment local = new MockEnvironment().withProperty("spring.profiles.active", "local");

        SettlementPayoutGateway gateway = config.settlementPayoutGateway(properties(PayoutMode.SIMULATED), local, null,
                new TossPayoutsProperties(), null, null);
        SettlementPartnerGateway partners = config.settlementPartnerGateway(properties(PayoutMode.SIMULATED), null,
                new TossPayoutsProperties());

        assertThat(gateway).isInstanceOf(SimulatedSettlementPayoutGateway.class);
        assertThat(partners).isInstanceOf(NoopSettlementPartnerGateway.class);
        assertThat(partners.partnerRefPrefix()).isEmpty();
        assertThat(partners.isExternal()).isFalse();
    }

    @Test
    @DisplayName("운영 프로필(prod) + 런칭 게이트 켜짐 + SIMULATED → 기동 실패")
    void simulatedInProdWithGateFails() {
        assertThatThrownBy(() -> config.settlementPayoutGateway(gated(PayoutMode.SIMULATED), prod(), null,
                new TossPayoutsProperties(), null, null))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("운영 프로필");
    }

    @Test
    @DisplayName("운영 프로필(prod) + 런칭 게이트 꺼짐(개발 단계 prod) + SIMULATED → 시뮬레이터로 뜬다(경고 로그만)")
    void simulatedInProdWithoutGateStarts() {
        SettlementPayoutGateway gateway = config.settlementPayoutGateway(properties(PayoutMode.SIMULATED), prod(), null,
                new TossPayoutsProperties(), null, null);

        assertThat(gateway).isInstanceOf(SimulatedSettlementPayoutGateway.class);
    }

    @Test
    @DisplayName("PG_TEST 는 test_sk_ 키만 · PG 는 live_sk_ 키만 — 접두가 다르면 기동 실패")
    void keyPrefixMustMatchMode() {
        assertThatThrownBy(() -> SettlementPayoutGatewayConfig.validateToss(PayoutMode.PG_TEST, toss("live_sk_abc", KEY_HEX)))
                .isInstanceOf(IllegalStateException.class).hasMessageContaining("test_sk_");
        assertThatThrownBy(() -> SettlementPayoutGatewayConfig.validateToss(PayoutMode.PG, toss("test_sk_abc", KEY_HEX)))
                .isInstanceOf(IllegalStateException.class).hasMessageContaining("live_sk_");
        assertThatCode(() -> SettlementPayoutGatewayConfig.validateToss(PayoutMode.PG_TEST, toss("test_sk_abc", KEY_HEX)))
                .doesNotThrowAnyException();
        assertThatCode(() -> SettlementPayoutGatewayConfig.validateToss(PayoutMode.PG, toss("live_sk_abc", KEY_HEX)))
                .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("시크릿 키 비어 있음 · 보안 키가 64 hex 아님 · 적요 7자 초과 → 기동 실패(네트워크 전)")
    void credentialsValidated() {
        assertThatThrownBy(() -> SettlementPayoutGatewayConfig.validateToss(PayoutMode.PG_TEST, toss("", KEY_HEX)))
                .isInstanceOf(IllegalStateException.class).hasMessageContaining("secret-key");
        assertThatThrownBy(() -> SettlementPayoutGatewayConfig.validateToss(PayoutMode.PG_TEST, toss("test_sk_abc", "abc")))
                .isInstanceOf(IllegalStateException.class).hasMessageContaining("security-key");
        assertThatThrownBy(() -> SettlementPayoutGatewayConfig.validateToss(PayoutMode.PG_TEST, toss("test_sk_abc", "zz" + KEY_HEX.substring(2))))
                .isInstanceOf(IllegalStateException.class).hasMessageContaining("hex");
        TossPayoutsProperties longDescription = toss("test_sk_abc", KEY_HEX);
        longDescription.setTransactionDescription("쇼룸즈정산입금입니다");
        assertThatThrownBy(() -> SettlementPayoutGatewayConfig.validateToss(PayoutMode.PG_TEST, longDescription))
                .isInstanceOf(IllegalStateException.class).hasMessageContaining("transaction-description");
    }

    @Test
    @DisplayName("PG + 테스트 대체 사업자번호가 차 있음 → 기동 실패(가짜 법인으로 운영 등록 방지) · PG_TEST 는 허용")
    void corporateOverrideOnlyInTest() {
        TossPayoutsProperties live = toss("live_sk_abc", KEY_HEX);
        live.getTest().setCorporateOverrideBrn("1234567890");
        assertThatThrownBy(() -> SettlementPayoutGatewayConfig.validateToss(PayoutMode.PG, live))
                .isInstanceOf(IllegalStateException.class).hasMessageContaining("corporate-override-brn");
        TossPayoutsProperties test = toss("test_sk_abc", KEY_HEX);
        test.getTest().setCorporateOverrideBrn("1234567890");
        assertThatCode(() -> SettlementPayoutGatewayConfig.validateToss(PayoutMode.PG_TEST, test)).doesNotThrowAnyException();
    }

    @Test
    @DisplayName("운영 프로필(prod) + 런칭 게이트 켜짐 + PG_TEST → 기동 실패(테스트 키로 운영 지급을 흉내 내지 않는다)")
    void pgTestInProdWithGateFails() {
        assertThatThrownBy(() -> config.tossPayoutsClient(gated(PayoutMode.PG_TEST), toss("test_sk_abc", KEY_HEX), prod(), null, null))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("운영 프로필");
    }

    private static MockEnvironment prod() {
        MockEnvironment prod = new MockEnvironment();
        prod.setActiveProfiles("prod");
        return prod;
    }

    private static SettlementProperties properties(PayoutMode mode) {
        SettlementProperties properties = new SettlementProperties();
        properties.getPayout().setMode(mode);
        return properties;
    }

    /** 런칭 게이트 켜짐 — 실제 돈이 오가는 운영 서버. */
    private static SettlementProperties gated(PayoutMode mode) {
        SettlementProperties properties = properties(mode);
        properties.setLaunchGateEnabled(true);
        return properties;
    }

    private static TossPayoutsProperties toss(String secretKey, String securityKey) {
        TossPayoutsProperties properties = new TossPayoutsProperties();
        properties.setSecretKey(secretKey);
        properties.setSecurityKey(securityKey);
        return properties;
    }
}
