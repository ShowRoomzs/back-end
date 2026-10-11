package showroomz.global.config;

import io.portone.sdk.server.errors.PlatformNotEnabledException;
import io.portone.sdk.server.platform.PlatformClient;
import io.portone.sdk.server.platform.PlatformSetting;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;
import org.springframework.core.env.Profiles;
import showroomz.domain.settlement.port.SettlementPartnerGateway;
import showroomz.domain.settlement.port.SettlementPayoutGateway;
import showroomz.global.config.properties.PortOnePlatformProperties;
import showroomz.global.config.properties.SettlementProperties;
import showroomz.global.config.properties.SettlementProperties.PayoutMode;
import showroomz.global.payment.portone.PaymentGatewayRejectedException;
import showroomz.global.payment.portone.PortOneProperties;
import showroomz.global.payment.settlement.NoopSettlementPartnerGateway;
import showroomz.global.payment.settlement.SimulatedSettlementPayoutGateway;
import showroomz.global.payment.settlement.portone.PortOnePartnerRegistrar;
import showroomz.global.payment.settlement.portone.PortOneSettlementPayoutGateway;

import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/**
 * 정산 지급 포트 선택(44 어드민 설계서 0-5 · 공통 결정 #9 · 포트원 설계서 6-4) — {@code settlement.payout.mode}.
 *
 * <ul>
 *   <li>{@code SIMULATED} — 즉시 지급 완료. <b>운영 프로필({@code prod})에서는 기동 실패</b> — 시뮬레이터가 운영에 새면 돈이 나간
 *       것처럼 기록된다</li>
 *   <li>{@code PG_TEST} — 포트원 파트너 정산 **테스트 모드**(전 호출 {@code test=true}). 운영 프로필에서는 기동 실패</li>
 *   <li>{@code PG} — 포트원 운영. 기동 시 {@code getPlatformSetting} 1회로 활성화 · {@code deductWht=false} 를 검증한다</li>
 * </ul>
 * 검증 방식은 {@code PortOneV2Gateway}의 {@code requireText}와 같다 — 설정이 틀렸으면 뜨지 않는다.
 */
@Slf4j
@Configuration
public class SettlementPayoutGatewayConfig {

    /** 포트원 Platform 클라이언트 — 결제 {@code PaymentClient} 와 별 인스턴스. 포트원 모드에서만 만든다. */
    @Bean
    @ConditionalOnExpression("'${settlement.payout.mode:SIMULATED}' != 'SIMULATED'")
    public PlatformClient settlementPlatformClient(SettlementProperties settlement, PortOneProperties portone,
                                                   PortOnePlatformProperties platform, Environment environment) {
        PayoutMode mode = settlement.getPayout().getMode();
        validateMode(settlement, environment);
        if (!portone.isEnabled()) {
            throw new IllegalStateException("settlement.payout.mode=" + mode + " 는 portone.enabled=true 가 필요합니다.");
        }
        requireText(portone.getStoreId(), "portone.store-id");
        String secret = platform.getApiSecret() == null || platform.getApiSecret().isBlank()
                ? portone.getApiSecret() : platform.getApiSecret();
        requireText(secret, "portone.api-secret(또는 portone.platform.api-secret)");
        PlatformClient client = new PlatformClient(secret, portone.getBaseUrl(), portone.getStoreId());
        if (platform.isStartupCheck()) {
            checkPlatform(client, mode == PayoutMode.PG_TEST, platform.getReadTimeoutMillis());
        }
        return client;
    }

    @Bean
    public SettlementPayoutGateway settlementPayoutGateway(SettlementProperties settlement, Environment environment,
                                                           ObjectProvider<PlatformClient> platformClient,
                                                           PortOnePlatformProperties platform) {
        PayoutMode mode = settlement.getPayout().getMode();
        validateMode(settlement, environment);
        if (mode == PayoutMode.SIMULATED) {
            return new SimulatedSettlementPayoutGateway();
        }
        boolean test = mode == PayoutMode.PG_TEST;
        return new PortOneSettlementPayoutGateway(platformClient.getObject(), test, prefix(platform, test),
                platform.getReadTimeoutMillis());
    }

    @Bean
    public SettlementPartnerGateway settlementPartnerGateway(SettlementProperties settlement,
                                                             ObjectProvider<PlatformClient> platformClient,
                                                             PortOnePlatformProperties platform) {
        PayoutMode mode = settlement.getPayout().getMode();
        if (mode == PayoutMode.SIMULATED) {
            return new NoopSettlementPartnerGateway();
        }
        boolean test = mode == PayoutMode.PG_TEST;
        return new PortOnePartnerRegistrar(platformClient.getObject(), test, prefix(platform, test),
                platform.getReadTimeoutMillis());
    }

    /**
     * 런칭 게이트가 켜진 운영 프로필은 {@code PG} 만 — 시뮬레이터 · 테스트 모드가 운영에 새면 지급이 된 것처럼 기록된다(44 어드민 0-5 ·
     * 포트원 6-4). 게이트를 끈 개발 단계 {@code prod} 는 경고만 남기고 뜬다.
     */
    static void validateMode(SettlementProperties settlement, Environment environment) {
        PayoutMode mode = settlement.getPayout().getMode();
        if (mode == PayoutMode.PG || !environment.acceptsProfiles(Profiles.of("prod"))) {
            return;
        }
        if (settlement.isLaunchGateEnabled()) {
            throw new IllegalStateException("settlement.payout.mode=" + mode + " 는 런칭 게이트가 켜진 운영 프로필(prod)에서 쓸 수 "
                    + "없습니다 — 운영은 PG(포트원 파트너 정산 운영 모드)만 됩니다(44 정산 설계서 0-5).");
        }
        log.warn("정산 런칭 게이트 꺼짐(settlement.launch-gate-enabled=false) — prod 인데 지급 모드 {} 입니다. 실제 돈은 나가지 않습니다. "
                + "실제 돈이 오가는 운영 서버에서는 반드시 켜고 PG 로 두세요(44 포트원 설계서 9-4).", mode);
    }

    /** 기동 검증 — 상점에 Platform 이 켜져 있고 원천징수를 포트원이 떼지 않는다(포트원 설계서 0-4 · 2-1). */
    static void checkPlatform(PlatformClient client, boolean test, long timeoutMillis) {
        PlatformSetting setting;
        try {
            setting = client.getPlatformSetting(test).get(timeoutMillis, TimeUnit.MILLISECONDS);
        } catch (ExecutionException e) {
            if (e.getCause() instanceof PlatformNotEnabledException) {
                throw new IllegalStateException("포트원 상점에 파트너 정산(Platform) 기능이 켜져 있지 않습니다 — 포트원에 활성화를 요청하세요"
                        + "(44 포트원 설계서 2-1). 그 전까지는 settlement.payout.mode=SIMULATED 로 두세요.", e);
            }
            throw new IllegalStateException("포트원 Platform 설정 조회 실패 — " + e.getCause().getMessage(), e.getCause());
        } catch (TimeoutException | InterruptedException e) {
            if (e instanceof InterruptedException) {
                Thread.currentThread().interrupt();
            }
            throw new IllegalStateException("포트원 Platform 설정 조회 타임아웃 — portone.platform.startup-check=false 로 끄거나 네트워크를 확인하세요", e);
        }
        if (setting.getDeductWht()) {
            throw new IllegalStateException("포트원 Platform 설정 deductWht=true — 원천징수는 우리 산식이 계산합니다. 콘솔에서 "
                    + "「원천징수 공제」를 끄세요(44 포트원 설계서 0-4). 켜진 채 지급하면 인플루언서 몫이 이중 공제됩니다.");
        }
        log.info("포트원 Platform 기동 검증 통과 - test: {}, deductWht: false", test);
    }

    private static String prefix(PortOnePlatformProperties platform, boolean test) {
        return test ? platform.getPartnerIdPrefixTest() : "";
    }

    private static void requireText(String value, String key) {
        if (value == null || value.isBlank()) {
            throw new IllegalStateException(key + " 설정이 비어 있습니다 — 포트원 파트너 정산 모드에 필요합니다.");
        }
    }

    /** 테스트 · 진단용 — 거절 예외의 원인이 「미활성화」인가. */
    static boolean isNotEnabled(PaymentGatewayRejectedException e) {
        return e.getCause() instanceof PlatformNotEnabledException;
    }
}
