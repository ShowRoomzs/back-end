package showroomz.domain.settlement;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;
import showroomz.domain.settlement.port.SettlementPartnerGateway;
import showroomz.domain.settlement.port.SettlementPayoutGateway;
import showroomz.global.config.SettlementPayoutGatewayConfig;
import showroomz.global.config.properties.InicisPayoutsProperties;
import showroomz.global.config.properties.SettlementProperties;
import showroomz.global.config.properties.SettlementProperties.PayoutMode;
import showroomz.global.payment.settlement.NoopSettlementPartnerGateway;
import showroomz.global.payment.settlement.SimulatedSettlementPayoutGateway;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 지급 모드 기동 검증(44 어드민 설계서 0-5 · 60 이니시스 전환 계획서 7절) — 설정이 틀렸으면 컨텍스트가 뜨지 않는다. PG 호출은 하지 않는다
 * (설정 검증이 네트워크 앞에서 끝난다).
 */
@DisplayName("정산 지급 모드 — SIMULATED · PG_TEST · PG 기동 검증 · 런칭 게이트 · 이니시스 호스트 · MID")
class SettlementPayoutGatewayConfigTest {

    private final SettlementPayoutGatewayConfig config = new SettlementPayoutGatewayConfig();

    @Test
    @DisplayName("SIMULATED · 비운영 프로필 → 시뮬레이터 · 셀러 포트는 Noop(연결 기록 없음)")
    void simulatedOutsideProd() {
        MockEnvironment local = new MockEnvironment().withProperty("spring.profiles.active", "local");

        SettlementPayoutGateway gateway = config.settlementPayoutGateway(properties(PayoutMode.SIMULATED), local, null,
                new InicisPayoutsProperties(), null);
        SettlementPartnerGateway partners = config.settlementPartnerGateway(properties(PayoutMode.SIMULATED), null,
                new InicisPayoutsProperties());

        assertThat(gateway).isInstanceOf(SimulatedSettlementPayoutGateway.class);
        assertThat(partners).isInstanceOf(NoopSettlementPartnerGateway.class);
        assertThat(partners.partnerRefPrefix()).isEmpty();
        assertThat(partners.isExternal()).isFalse();
        assertThat(gateway.leadBusinessDays()).isZero();
        assertThat(gateway.uploadCutoff()).isEmpty();
    }

    @Test
    @DisplayName("이니시스 PG — 운영 호스트 · MID · 키 · 예금주 검증 확정이면 통과 / 스테이징 URL · assume-paid · 예금주 검증 미확정이면 기동 실패")
    void inicisProductionValidated() {
        assertThatCode(() -> SettlementPayoutGatewayConfig.validateInicis(PayoutMode.PG, inicis("https://apiwi.inicis.com", true)))
                .doesNotThrowAnyException();
        assertThatThrownBy(() -> SettlementPayoutGatewayConfig.validateInicis(PayoutMode.PG,
                inicis("https://stg-apiwi.inicis.com", true)))
                .isInstanceOf(IllegalStateException.class).hasMessageContaining("운영 호스트");
        InicisPayoutsProperties assumePaid = inicis("https://apiwi.inicis.com", true);
        assumePaid.getTest().setAssumePaid(true);
        assertThatThrownBy(() -> SettlementPayoutGatewayConfig.validateInicis(PayoutMode.PG, assumePaid))
                .isInstanceOf(IllegalStateException.class).hasMessageContaining("assume-paid");
        assertThatThrownBy(() -> SettlementPayoutGatewayConfig.validateInicis(PayoutMode.PG, inicis("https://apiwi.inicis.com", false)))
                .isInstanceOf(IllegalStateException.class).hasMessageContaining("holder-verified-by-upsert");
    }

    @Test
    @DisplayName("이니시스 PG_TEST — 운영 호스트면 기동 실패(테스트 모드로 실제 지급 방지) · 스테이징 URL 은 예금주 검증 미확정 · assume-paid 여도 통과")
    void inicisStagingValidated() {
        assertThatThrownBy(() -> SettlementPayoutGatewayConfig.validateInicis(PayoutMode.PG_TEST, inicis("https://apiwi.inicis.com", false)))
                .isInstanceOf(IllegalStateException.class).hasMessageContaining("스테이징");
        InicisPayoutsProperties staging = inicis("https://stg-apiwi.inicis.com", false);
        staging.getTest().setAssumePaid(true);
        assertThatCode(() -> SettlementPayoutGatewayConfig.validateInicis(PayoutMode.PG_TEST, staging)).doesNotThrowAnyException();
    }

    @Test
    @DisplayName("이니시스 MID · API 키 비어 있음 · MID 10자 초과 → 기동 실패(네트워크 전)")
    void inicisCredentialsValidated() {
        InicisPayoutsProperties noMid = inicis("https://stg-apiwi.inicis.com", false);
        noMid.setMerchantId(" ");
        assertThatThrownBy(() -> SettlementPayoutGatewayConfig.validateInicis(PayoutMode.PG_TEST, noMid))
                .isInstanceOf(IllegalStateException.class).hasMessageContaining("merchant-id");
        InicisPayoutsProperties longMid = inicis("https://stg-apiwi.inicis.com", false);
        longMid.setMerchantId("INIpayTest01");
        assertThatThrownBy(() -> SettlementPayoutGatewayConfig.validateInicis(PayoutMode.PG_TEST, longMid))
                .isInstanceOf(IllegalStateException.class).hasMessageContaining("10자");
        InicisPayoutsProperties noKey = inicis("https://stg-apiwi.inicis.com", false);
        noKey.setApiKey(null);
        assertThatThrownBy(() -> SettlementPayoutGatewayConfig.validateInicis(PayoutMode.PG_TEST, noKey))
                .isInstanceOf(IllegalStateException.class).hasMessageContaining("api-key");
    }

    @Test
    @DisplayName("운영 프로필(prod) + 런칭 게이트 켜짐 + 이니시스 PG_TEST → 기동 실패")
    void inicisPgTestInProdWithGateFails() {
        SettlementProperties gated = gated(PayoutMode.PG_TEST);
        assertThatThrownBy(() -> config.inicisPayoutsClient(gated, inicis("https://stg-apiwi.inicis.com", false), prod(), null, null))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("운영 프로필");
    }

    @Test
    @DisplayName("운영 프로필(prod) + 런칭 게이트 켜짐 + SIMULATED → 기동 실패")
    void simulatedInProdWithGateFails() {
        assertThatThrownBy(() -> config.settlementPayoutGateway(gated(PayoutMode.SIMULATED), prod(), null,
                new InicisPayoutsProperties(), null))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("운영 프로필");
    }

    @Test
    @DisplayName("운영 프로필(prod) + 런칭 게이트 꺼짐(개발 단계 prod) + SIMULATED → 시뮬레이터로 뜬다(경고 로그만)")
    void simulatedInProdWithoutGateStarts() {
        SettlementPayoutGateway gateway = config.settlementPayoutGateway(properties(PayoutMode.SIMULATED), prod(), null,
                new InicisPayoutsProperties(), null);

        assertThat(gateway).isInstanceOf(SimulatedSettlementPayoutGateway.class);
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

    private static InicisPayoutsProperties inicis(String baseUrl, boolean holderVerified) {
        InicisPayoutsProperties properties = new InicisPayoutsProperties();
        properties.setBaseUrl(baseUrl);
        properties.setMerchantId("INIpayTest");
        properties.setApiKey("test-api-key");
        properties.setHolderVerifiedByUpsert(holderVerified);
        return properties;
    }
}
