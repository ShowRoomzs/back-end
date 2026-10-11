package showroomz.domain.settlement;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;
import showroomz.domain.settlement.port.SettlementPayoutGateway;
import showroomz.global.config.SettlementPayoutGatewayConfig;
import showroomz.global.config.properties.SettlementProperties;
import showroomz.global.payment.settlement.SimulatedSettlementPayoutGateway;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** 지급 모드 기동 검증(44 어드민 설계서 0-5 · 구현 계획서 5-2 「모드」) — 설정이 틀렸으면 컨텍스트가 뜨지 않는다. */
@DisplayName("정산 지급 모드 — PG 어댑터 없음 · 운영 프로필 SIMULATED 금지")
class SettlementPayoutGatewayConfigTest {

    private final SettlementPayoutGatewayConfig config = new SettlementPayoutGatewayConfig();

    @Test
    @DisplayName("SIMULATED · 비운영 프로필 → 시뮬레이터")
    void simulatedOutsideProd() {
        SettlementPayoutGateway gateway = config.settlementPayoutGateway(properties(SettlementProperties.PayoutMode.SIMULATED),
                new MockEnvironment().withProperty("spring.profiles.active", "local"));

        assertThat(gateway).isInstanceOf(SimulatedSettlementPayoutGateway.class);
    }

    @Test
    @DisplayName("PG → 어댑터가 없어 기동 실패")
    void pgModeFails() {
        assertThatThrownBy(() -> config.settlementPayoutGateway(properties(SettlementProperties.PayoutMode.PG),
                new MockEnvironment()))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("PG 지급대행 어댑터");
    }

    @Test
    @DisplayName("운영 프로필(prod) + SIMULATED → 기동 실패")
    void simulatedInProdFails() {
        MockEnvironment prod = new MockEnvironment();
        prod.setActiveProfiles("prod");

        assertThatThrownBy(() -> config.settlementPayoutGateway(properties(SettlementProperties.PayoutMode.SIMULATED),
                prod))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("운영 프로필");
    }

    private static SettlementProperties properties(SettlementProperties.PayoutMode mode) {
        SettlementProperties properties = new SettlementProperties();
        properties.getPayout().setMode(mode);
        return properties;
    }
}
