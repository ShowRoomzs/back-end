package showroomz.global.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;
import org.springframework.core.env.Profiles;
import org.springframework.web.client.RestClient;
import showroomz.domain.settlement.port.SettlementPartnerGateway;
import showroomz.domain.settlement.port.SettlementPayoutGateway;
import showroomz.global.config.properties.InicisPayoutsProperties;
import showroomz.global.config.properties.SettlementProperties;
import showroomz.global.config.properties.SettlementProperties.PayoutMode;
import showroomz.global.config.properties.SettlementProperties.PayoutProvider;
import showroomz.global.config.properties.TossPayoutsProperties;
import showroomz.global.payment.portone.PaymentGatewayException;
import showroomz.global.payment.portone.PaymentGatewayRejectedException;
import showroomz.global.payment.settlement.NoopSettlementPartnerGateway;
import showroomz.global.payment.settlement.SimulatedSettlementPayoutGateway;
import showroomz.global.payment.settlement.inicis.InicisPayoutsClient;
import showroomz.global.payment.settlement.inicis.InicisSettlementPayoutGateway;
import showroomz.global.payment.settlement.inicis.InicisSubMallRegistrar;
import showroomz.global.payment.settlement.toss.TossJwe;
import showroomz.global.payment.settlement.toss.TossPayoutsClient;
import showroomz.global.payment.settlement.toss.TossSellerRegistrar;
import showroomz.global.payment.settlement.toss.TossSettlementPayoutGateway;
import showroomz.global.utils.BusinessCalendar;

import java.time.LocalDate;

/**
 * 정산 지급 포트 선택(44 어드민 설계서 0-5 · 공통 결정 #9 · 토스 설계서 6-4 · 60 이니시스 전환 계획서 7절) — {@code settlement.payout.mode} ·
 * {@code settlement.payout.provider}(전환 기간 · 기본 {@code TOSS} · D-14). 빈은 provider 하나만 만든다.
 *
 * <ul>
 *   <li>{@code SIMULATED} — 즉시 지급 완료(묶음 경로). <b>런칭 게이트 켜진 운영 프로필({@code prod})에서는 기동 실패</b></li>
 *   <li>{@code PG_TEST} — 토스 <b>테스트 키</b>({@code test_sk_}) / 이니시스 <b>스테이징</b>(운영 호스트면 기동 실패). 런칭 게이트 켜진 운영 프로필에서는 기동 실패</li>
 *   <li>{@code PG} — 토스 운영({@code live_sk_}) / 이니시스 운영({@code apiwi.inicis.com} · {@code assume-paid} 꺼짐 · 예금주 검증 확정)</li>
 * </ul>
 * 검증 방식은 {@code PortOneV2Gateway}의 {@code requireText}와 같다 — 설정이 틀렸으면 뜨지 않는다. 기동 시 PG 호출은 잔액 조회 1회
 * (토스 {@code GET /v2/balances} · 이니시스 {@code paycheck} — 이니시스 스테이징은 조회가 막혀 있어 건너뛴다).
 */
@Slf4j
@Configuration
public class SettlementPayoutGatewayConfig {

    /** 토스 지급대행 클라이언트 — PG 모드 · provider TOSS 에서만 만든다. */
    @Bean
    @ConditionalOnExpression("'${settlement.payout.mode:SIMULATED}' != 'SIMULATED' and '${settlement.payout.provider:TOSS}' == 'TOSS'")
    public TossPayoutsClient tossPayoutsClient(SettlementProperties settlement, TossPayoutsProperties toss,
                                               Environment environment, RestClient.Builder builder, ObjectMapper objectMapper) {
        PayoutMode mode = settlement.getPayout().getMode();
        validateMode(settlement, environment);
        validateToss(mode, toss);
        TossPayoutsClient client = new TossPayoutsClient(toss, builder.requestFactory(TossPayoutsClient.requestFactory(toss)), objectMapper);
        if (toss.isStartupCheck()) {
            checkContract(client, mode);
        }
        return client;
    }

    /** 이니시스 지급대행 클라이언트 — PG 모드 · provider INICIS 에서만 만든다(60 전환 계획서 7절). */
    @Bean
    @ConditionalOnExpression("'${settlement.payout.mode:SIMULATED}' != 'SIMULATED' and '${settlement.payout.provider:TOSS}' == 'INICIS'")
    public InicisPayoutsClient inicisPayoutsClient(SettlementProperties settlement, InicisPayoutsProperties inicis,
                                                   Environment environment, RestClient.Builder builder, ObjectMapper objectMapper) {
        PayoutMode mode = settlement.getPayout().getMode();
        validateMode(settlement, environment);
        validateInicis(mode, inicis);
        InicisPayoutsClient client = new InicisPayoutsClient(inicis,
                builder.requestFactory(InicisPayoutsClient.requestFactory(inicis)), objectMapper);
        if (inicis.isStartupCheck() && mode == PayoutMode.PG) {
            checkInicisContract(client);
        } else if (mode == PayoutMode.PG_TEST) {
            log.info("이니시스 지급대행 스테이징(PG_TEST) — 잔액 · 결과 · AML 조회가 막혀 있어 기동 잔액 검증을 건너뛴다(60 전환 계획서 1-8)");
        }
        return client;
    }

    @Bean
    public SettlementPayoutGateway settlementPayoutGateway(SettlementProperties settlement, Environment environment,
                                                           ObjectProvider<TossPayoutsClient> client, TossPayoutsProperties toss,
                                                           ObjectProvider<InicisPayoutsClient> inicisClient,
                                                           InicisPayoutsProperties inicis,
                                                           BusinessCalendar businessCalendar, ObjectMapper objectMapper) {
        PayoutMode mode = settlement.getPayout().getMode();
        validateMode(settlement, environment);
        if (mode == PayoutMode.SIMULATED) {
            return new SimulatedSettlementPayoutGateway();
        }
        if (settlement.getPayout().getProvider() == PayoutProvider.INICIS) {
            return new InicisSettlementPayoutGateway(inicisClient.getObject(), mode.isTest(), inicis, businessCalendar);
        }
        return new TossSettlementPayoutGateway(client.getObject(), mode.isTest(), toss, businessCalendar, objectMapper);
    }

    @Bean
    public SettlementPartnerGateway settlementPartnerGateway(SettlementProperties settlement,
                                                             ObjectProvider<TossPayoutsClient> client, TossPayoutsProperties toss,
                                                             ObjectProvider<InicisPayoutsClient> inicisClient,
                                                             InicisPayoutsProperties inicis) {
        PayoutMode mode = settlement.getPayout().getMode();
        if (mode == PayoutMode.SIMULATED) {
            return new NoopSettlementPartnerGateway();
        }
        if (settlement.getPayout().getProvider() == PayoutProvider.INICIS) {
            return new InicisSubMallRegistrar(inicisClient.getObject(), mode.isTest(), inicis);
        }
        return new TossSellerRegistrar(client.getObject(), mode.isTest(), toss);
    }

    /**
     * 런칭 게이트가 켜진 운영 프로필은 {@code PG} 만 — 시뮬레이터 · 테스트 키가 운영에 새면 지급이 된 것처럼 기록된다(44 어드민 0-5 ·
     * 토스 9-4). 게이트를 끈 개발 단계 {@code prod} 는 경고만 남기고 뜬다.
     */
    static void validateMode(SettlementProperties settlement, Environment environment) {
        PayoutMode mode = settlement.getPayout().getMode();
        if (mode == PayoutMode.PG || !environment.acceptsProfiles(Profiles.of("prod"))) {
            return;
        }
        if (settlement.isLaunchGateEnabled()) {
            throw new IllegalStateException("settlement.payout.mode=" + mode + " 는 런칭 게이트가 켜진 운영 프로필(prod)에서 쓸 수 "
                    + "없습니다 — 운영은 PG(지급대행 운영)만 됩니다(44 정산 설계서 0-5).");
        }
        log.warn("정산 런칭 게이트 꺼짐(settlement.launch-gate-enabled=false) — prod 인데 지급 모드 {} 입니다. 실제 돈은 나가지 않습니다. "
                + "실제 돈이 오가는 운영 서버에서는 반드시 켜고 PG 로 두세요(44 토스 설계서 9-4).", mode);
    }

    /** 키 · 보안 키 · 적요 · 테스트 대체 사업자번호 — 네트워크 앞에서 끝나는 검증(6-4). */
    public static void validateToss(PayoutMode mode, TossPayoutsProperties toss) {
        requireText(toss.getSecretKey(), "toss.payouts.secret-key");
        if (mode == PayoutMode.PG_TEST && !toss.isTestKey()) {
            throw new IllegalStateException("settlement.payout.mode=PG_TEST 는 토스 테스트 키(test_sk_…)만 받습니다 — "
                    + "라이브 키로 테스트 모드를 돌리면 실제 지급이 나갑니다(44 토스 설계서 6-4).");
        }
        if (mode == PayoutMode.PG && !toss.isLiveKey()) {
            throw new IllegalStateException("settlement.payout.mode=PG 는 토스 라이브 키(live_sk_…)만 받습니다(44 토스 설계서 6-4).");
        }
        if (mode == PayoutMode.PG && toss.getTest().hasCorporateOverride()) {
            throw new IllegalStateException("toss.payouts.test.corporate-override-brn 은 PG_TEST 전용입니다 — 운영(PG)에서는 비우세요. "
                    + "비법인 수취자가 가짜 법인으로 등록됩니다(44 토스 설계서 0-10).");
        }
        try {
            TossJwe.parseKey(toss.getSecurityKey());
        } catch (IllegalArgumentException e) {
            throw new IllegalStateException(e.getMessage() + " — 개발자센터 「API 개별 키 > 보안 키」(44 토스 설계서 2-1).", e);
        }
        String description = toss.getTransactionDescription();
        if (description == null || description.isBlank()
                || description.length() > TossPayoutsProperties.TRANSACTION_DESCRIPTION_MAX) {
            throw new IllegalStateException("toss.payouts.transaction-description 은 1~7자여야 합니다(이체 적요 · 44 토스 설계서 1-4).");
        }
    }

    /**
     * 이니시스 설정 검증(60 전환 계획서 7절 · D-5 · D-10) — 네트워크 앞에서 끝난다. 키 접두로 환경을 가를 수 없어 base URL 호스트로 가른다.
     */
    public static void validateInicis(PayoutMode mode, InicisPayoutsProperties inicis) {
        String mid = inicis.getMerchantId();
        if (mid == null || mid.isBlank()) {
            throw new IllegalStateException("inicis.payouts.merchant-id 설정이 비어 있습니다 — 이니시스 지급대행 모드에 필요합니다.");
        }
        if (mid.trim().length() > InicisPayoutsProperties.MERCHANT_ID_MAX) {
            throw new IllegalStateException("inicis.payouts.merchant-id 는 " + InicisPayoutsProperties.MERCHANT_ID_MAX
                    + "자 이하여야 합니다(idMerchant).");
        }
        String apiKey = inicis.getApiKey();
        if (apiKey == null || apiKey.isBlank()) {
            throw new IllegalStateException("inicis.payouts.api-key 설정이 비어 있습니다 — 가맹점관리자 「부가서비스 연동 KEY 정보 > 지급대행 서비스」.");
        }
        if (mode == PayoutMode.PG) {
            if (!inicis.isProductionHost()) {
                throw new IllegalStateException("settlement.payout.mode=PG 는 이니시스 운영 호스트(" + InicisPayoutsProperties.PRODUCTION_HOST
                        + ")만 받습니다 — inicis.payouts.base-url 을 확인하세요(60 전환 계획서 7절).");
            }
            if (inicis.getTest().isAssumePaid()) {
                throw new IllegalStateException("inicis.payouts.test.assume-paid 는 PG_TEST 전용입니다 — 운영(PG)에서 켜면 송금 결과 없이 지급 완료로 "
                        + "기록됩니다(60 전환 계획서 D-10).");
            }
            if (!inicis.isHolderVerifiedByUpsert()) {
                throw new IllegalStateException("이니시스 서브몰 등록의 예금주 검증이 확정되지 않았습니다(inicis.payouts.holder-verified-by-upsert=false) — "
                        + "은행 이체는 예금주를 대조하지 않아 오입금이 그대로 나갑니다. Q7 답으로 확정하거나 별도 예금주 조회를 붙인 뒤 켜세요"
                        + "(60 전환 계획서 D-5 · 런칭 차단).");
            }
        }
        if (mode == PayoutMode.PG_TEST && inicis.isProductionHost()) {
            throw new IllegalStateException("settlement.payout.mode=PG_TEST 는 이니시스 운영 호스트를 쓸 수 없습니다 — 테스트 모드로 운영에 올리면 실제 "
                    + "지급이 나갑니다. 계약담당자가 준 스테이징 URL 을 inicis.payouts.base-url 에 넣으세요(60 전환 계획서 D-10).");
        }
    }

    /** 이니시스 기동 검증 — 인증 · 계약({@code paycheck} 1회). 거절 코드별 문구는 코드표(Q5) 받은 뒤 나눈다. */
    static void checkInicisContract(InicisPayoutsClient client) {
        LocalDate today = showroomz.global.utils.KstDates.today();
        try {
            var check = client.payCheck(today, today).body();
            log.info("이니시스 지급대행 기동 검증 통과 · PG - totalAmt: {}", check == null ? null : check.totalAmt());
        } catch (PaymentGatewayRejectedException e) {
            throw new IllegalStateException("이니시스 지급대행 잔액 대사 거절(" + e.getType() + ") — MID · API 키 · 지급대행 특약 계약을 확인하세요. "
                    + "그 전까지는 settlement.payout.mode=SIMULATED 로 두세요(60 전환 계획서 7절).", e);
        } catch (PaymentGatewayException e) {
            throw new IllegalStateException("이니시스 지급대행 잔액 대사 실패 — inicis.payouts.startup-check=false 로 끄거나 네트워크 · 방화벽"
                    + "(118.129.210.143 · 183.109.71.186)을 확인하세요. " + e.getMessage(), e);
        }
    }

    /** 기동 검증 — 인증 · 지급대행 계약(토스 설계서 2-1). */
    static void checkContract(TossPayoutsClient client, PayoutMode mode) {
        try {
            long available = client.balance().available();
            log.info("토스 지급대행 기동 검증 통과 - mode: {}, availableAmount: {}", mode, available);
        } catch (PaymentGatewayRejectedException e) {
            if (e.getHttpStatus() == 401) {
                throw new IllegalStateException("토스 지급대행 인증 실패(UNAUTHORIZED_KEY) — toss.payouts.secret-key 를 확인하세요.", e);
            }
            if (e.getHttpStatus() == 403) {
                throw new IllegalStateException("토스 지급대행 계약이 없습니다(FORBIDDEN_REQUEST) — 계약 뒤 켜세요. 그 전까지는 "
                        + "settlement.payout.mode=SIMULATED 로 두세요(44 토스 설계서 2-1).", e);
            }
            throw new IllegalStateException("토스 지급대행 잔액 조회 거절 — " + e.getMessage(), e);
        } catch (PaymentGatewayException e) {
            throw new IllegalStateException("토스 지급대행 잔액 조회 실패 — toss.payouts.startup-check=false 로 끄거나 네트워크를 확인하세요. "
                    + e.getMessage(), e);
        }
    }

    private static void requireText(String value, String key) {
        if (value == null || value.isBlank()) {
            throw new IllegalStateException(key + " 설정이 비어 있습니다 — 토스 지급대행 모드에 필요합니다.");
        }
    }
}
