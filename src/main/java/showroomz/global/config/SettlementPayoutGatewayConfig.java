package showroomz.global.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;
import org.springframework.core.env.Profiles;
import showroomz.domain.settlement.port.SettlementPayoutGateway;
import showroomz.global.config.properties.SettlementProperties;
import showroomz.global.config.properties.SettlementProperties.PayoutMode;
import showroomz.global.payment.settlement.SimulatedSettlementPayoutGateway;

/**
 * 정산 지급 포트 선택(44 어드민 설계서 0-5 · 공통 결정 #9) — {@code settlement.payout.mode}.
 *
 * <ul>
 *   <li>{@code PG} — 지급대행(3자 분배) 어댑터가 <b>아직 없다</b>(PG 계약 · 13절 A-1 · 런칭 게이트) → 기동 실패</li>
 *   <li>{@code SIMULATED} — 즉시 지급 완료. <b>운영 프로필({@code prod})에서는 기동 실패</b> — 시뮬레이터가 운영에 새면 돈이 나간
 *       것처럼 기록된다(13-1 리스크). 그래서 PG 어댑터가 붙기 전까지 운영 배포는 불가다</li>
 * </ul>
 * 검증 방식은 {@code PortOneV2Gateway}의 {@code requireText}와 같다 — 설정이 틀렸으면 뜨지 않는다.
 */
@Configuration
public class SettlementPayoutGatewayConfig {

    @Bean
    public SettlementPayoutGateway settlementPayoutGateway(SettlementProperties properties, Environment environment) {
        PayoutMode mode = properties.getPayout().getMode();
        if (mode == PayoutMode.PG) {
            throw new IllegalStateException("settlement.payout.mode=PG — PG 지급대행 어댑터가 아직 없습니다(44 정산 설계서 13절 A-1). "
                    + "개발 · QA 는 SIMULATED 로 두세요.");
        }
        if (environment.acceptsProfiles(Profiles.of("prod"))) {
            throw new IllegalStateException("settlement.payout.mode=SIMULATED 는 운영 프로필(prod)에서 쓸 수 없습니다 — "
                    + "PG 지급대행 연동 전 운영 배포 불가(44 정산 설계서 0-5).");
        }
        return new SimulatedSettlementPayoutGateway();
    }
}
