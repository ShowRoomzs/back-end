package showroomz.domain.settlement;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;
import showroomz.domain.settlement.port.SettlementPartnerGateway;
import showroomz.domain.settlement.port.SettlementPayoutGateway;
import showroomz.global.config.SettlementPayoutGatewayConfig;
import showroomz.global.config.properties.PortOnePlatformProperties;
import showroomz.global.config.properties.SettlementProperties;
import showroomz.global.config.properties.SettlementProperties.PayoutMode;
import showroomz.global.payment.portone.PortOneProperties;
import showroomz.global.payment.settlement.NoopSettlementPartnerGateway;
import showroomz.global.payment.settlement.SimulatedSettlementPayoutGateway;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 지급 모드 기동 검증(44 어드민 설계서 0-5 · 포트원 설계서 6-4) — 설정이 틀렸으면 컨텍스트가 뜨지 않는다. 포트원 호출은 하지 않는다
 * (설정 검증이 네트워크 앞에서 끝난다).
 */
@DisplayName("정산 지급 모드 — SIMULATED · PG_TEST · PG 기동 검증 · 런칭 게이트")
class SettlementPayoutGatewayConfigTest {

    private final SettlementPayoutGatewayConfig config = new SettlementPayoutGatewayConfig();

    @Test
    @DisplayName("SIMULATED · 비운영 프로필 → 시뮬레이터 · 파트너 포트는 Noop")
    void simulatedOutsideProd() {
        MockEnvironment local = new MockEnvironment().withProperty("spring.profiles.active", "local");

        SettlementPayoutGateway gateway = config.settlementPayoutGateway(properties(PayoutMode.SIMULATED), local, null,
                new PortOnePlatformProperties());
        SettlementPartnerGateway partners = config.settlementPartnerGateway(properties(PayoutMode.SIMULATED), null,
                new PortOnePlatformProperties());

        assertThat(gateway).isInstanceOf(SimulatedSettlementPayoutGateway.class);
        assertThat(partners).isInstanceOf(NoopSettlementPartnerGateway.class);
        assertThat(partners.partnerIdPrefix()).isEmpty();
    }

    @Test
    @DisplayName("운영 프로필(prod) + 런칭 게이트 켜짐 + SIMULATED → 기동 실패")
    void simulatedInProdWithGateFails() {
        assertThatThrownBy(() -> config.settlementPayoutGateway(gated(PayoutMode.SIMULATED), prod(), null,
                new PortOnePlatformProperties()))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("운영 프로필");
    }

    @Test
    @DisplayName("운영 프로필(prod) + 런칭 게이트 꺼짐(개발 단계 prod) + SIMULATED → 시뮬레이터로 뜬다(경고 로그만)")
    void simulatedInProdWithoutGateStarts() {
        SettlementPayoutGateway gateway = config.settlementPayoutGateway(properties(PayoutMode.SIMULATED), prod(), null,
                new PortOnePlatformProperties());

        assertThat(gateway).isInstanceOf(SimulatedSettlementPayoutGateway.class);
    }

    @Test
    @DisplayName("운영 프로필(prod) + 런칭 게이트 켜짐 + PG_TEST → 기동 실패(테스트 데이터로 운영 지급을 흉내 내지 않는다)")
    void pgTestInProdWithGateFails() {
        assertThatThrownBy(() -> config.settlementPlatformClient(gated(PayoutMode.PG_TEST), portone(true, "store"),
                new PortOnePlatformProperties(), prod()))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("운영 프로필");
    }

    @Test
    @DisplayName("PG_TEST + portone.enabled=false → 기동 실패")
    void portOneDisabledFails() {
        assertThatThrownBy(() -> config.settlementPlatformClient(properties(PayoutMode.PG_TEST), portone(false, "store"),
                new PortOnePlatformProperties(), new MockEnvironment()))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("portone.enabled");
    }

    @Test
    @DisplayName("PG + 상점 id · Secret 비어 있음 → 기동 실패(네트워크 전)")
    void pgWithoutCredentialsFails() {
        assertThatThrownBy(() -> config.settlementPlatformClient(properties(PayoutMode.PG), portone(true, ""),
                new PortOnePlatformProperties(), new MockEnvironment()))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("portone.store-id");
        PortOneProperties noSecret = portone(true, "store");
        noSecret.setApiSecret("");
        assertThatThrownBy(() -> config.settlementPlatformClient(properties(PayoutMode.PG), noSecret,
                new PortOnePlatformProperties(), new MockEnvironment()))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("api-secret");
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

    private static PortOneProperties portone(boolean enabled, String storeId) {
        PortOneProperties properties = new PortOneProperties();
        properties.setEnabled(enabled);
        properties.setStoreId(storeId);
        properties.setApiSecret("secret");
        return properties;
    }
}
