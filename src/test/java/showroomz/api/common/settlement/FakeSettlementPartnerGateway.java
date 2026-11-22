package showroomz.api.common.settlement;

import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Component;
import showroomz.domain.settlement.port.SettlementPartnerGateway;
import showroomz.domain.settlement.service.SettlementPartnerIds;
import showroomz.domain.settlement.type.AmlThresholdAction;
import showroomz.domain.settlement.type.PayoutBlockReason;
import showroomz.domain.settlement.type.SettlementPayee;
import showroomz.global.config.properties.InicisPayoutsProperties;
import showroomz.global.payment.settlement.inicis.InicisSubMallRegistrar;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * 테스트용 셀러 · 서브몰 포트(토스 설계서 8-1 · 60 이니시스 전환 계획서 9-2) — 수취자별 실패 사유 · 대기 상태를 심고, 호출된 프로필을 기록한다.
 * 컨텍스트 공용 빈이라 테스트마다 {@link #reset()}한다.
 *
 * <ul>
 *   <li><b>토스 흉내(기본)</b> — 즉시 승인(토스 {@code PARTIALLY_APPROVED} · 발급 id {@code FAKE-SELLER-{ref}}). 상태 문자열은 토스 것을 그대로 쓴다 —
 *   {@code SettlementPartnerSyncService} 가 저장하는 원시값과 웹훅 {@code seller.changed} 가 같은 낱말이어야 통합 테스트가 운영과 같은 경로를 탄다.</li>
 *   <li><b>이니시스 흉내</b>({@link #inicisMode()}) — PG 구분 {@code INICIS} · {@code idMall = t b{id} / t c{id}}(id = 참조) · 원시 상태 {@code REGISTERED} ·
 *   필수값 검사는 실제 어댑터의 사전 검사({@link InicisSubMallRegistrar#precheck})를 그대로 쓴다 · AML 상태는 {@link #amlFor} 로 심는다.</li>
 * </ul>
 */
@Primary
@Component
public class FakeSettlementPartnerGateway implements SettlementPartnerGateway {

    public static final String APPROVAL_REQUIRED = "APPROVAL_REQUIRED";
    public static final String PARTIALLY_APPROVED = "PARTIALLY_APPROVED";
    public static final String KYC_REQUIRED = "KYC_REQUIRED";
    public static final String APPROVED = "APPROVED";
    public static final String REGISTERED = InicisSubMallRegistrar.STATUS_REGISTERED;
    /** 이니시스 흉내 AML 원시값 — 이행 유효(Y) / 만료(N). */
    public static final String AML_VALID = "EY|RN|XY";
    public static final String AML_EXPIRED = "EY|RN|XN";

    private final Map<SettlementPayee, PartnerResult> scripted = new EnumMap<>(SettlementPayee.class);
    private final Map<SettlementPayee, String> aml = new EnumMap<>(SettlementPayee.class);
    private final List<PartnerProfile> calls = new ArrayList<>();
    private final InicisSubMallRegistrar inicisRules;
    private String env = "TEST";
    private boolean inicis;
    private Runnable beforeNextEnsure;

    public FakeSettlementPartnerGateway() {
        InicisPayoutsProperties properties = new InicisPayoutsProperties();
        properties.setMerchantId("INIpayTest");
        properties.setApiKey("fake");
        this.inicisRules = new InicisSubMallRegistrar(null, true, properties);
    }

    @Override
    public String env() {
        return env;
    }

    @Override
    public synchronized String provider() {
        return inicis ? "INICIS" : "TOSS";
    }

    @Override
    public synchronized String partnerRefPrefix() {
        return inicis ? "t" : "";
    }

    @Override
    public synchronized String partnerRef(SettlementPayee payee, Long refId) {
        return inicis ? SettlementPartnerIds.subMallId("t", payee, refId)
                : SettlementPartnerIds.partnerRef("", payee, refId);
    }

    /** 이니시스 흉내로. */
    public synchronized void inicisMode() {
        inicis = true;
    }

    @Override
    public synchronized PartnerResult ensure(PartnerProfile profile) {
        calls.add(profile);
        Runnable hook = beforeNextEnsure;
        beforeNextEnsure = null;
        if (hook != null) {
            hook.run();
        }
        PartnerResult script = scripted.get(profile.payee());
        if (inicis) {
            if (script != null) {
                return script.hasExternalId() || script.status() == PartnerStatus.FAILED ? script
                        : new PartnerResult(script.status(), profile.partnerRef(), script.rawStatus(), script.failCode(), script.failReason());
            }
            PartnerResult precheck = inicisRules.precheck(profile);
            return precheck != null ? precheck : PartnerResult.approved(profile.partnerRef(), REGISTERED);
        }
        if (script != null) {
            return script.hasExternalId() || script.status() == PartnerStatus.FAILED ? script
                    : new PartnerResult(script.status(), externalIdOf(profile), script.rawStatus(), script.failCode(), script.failReason());
        }
        return PartnerResult.approved(externalIdOf(profile), PARTIALLY_APPROVED);
    }

    @Override
    public synchronized PartnerStatus statusOf(String rawStatus) {
        if (rawStatus == null) {
            return PartnerStatus.FAILED;
        }
        return switch (rawStatus) {
            case APPROVED, PARTIALLY_APPROVED, REGISTERED -> PartnerStatus.APPROVED;
            case APPROVAL_REQUIRED, KYC_REQUIRED -> PartnerStatus.PENDING;
            default -> PartnerStatus.REJECTED;
        };
    }

    @Override
    public String blockCodeOf(String rawStatus) {
        return KYC_REQUIRED.equals(rawStatus) ? PayoutBlockReason.KYC_REQUIRED.name()
                : PayoutBlockReason.IDENTITY_VERIFICATION_REQUIRED.name();
    }

    @Override
    public synchronized boolean isLimited(String rawStatus) {
        return !inicis && PARTIALLY_APPROVED.equals(rawStatus);
    }

    @Override
    public synchronized AmlThresholdAction amlThresholdAction() {
        return inicis ? AmlThresholdAction.ALERT : AmlThresholdAction.NONE;
    }

    @Override
    public synchronized Optional<AmlState> amlStatus(String externalId) {
        if (!inicis || externalId == null) {
            return Optional.empty();
        }
        SettlementPayee payee = externalId.startsWith("tb") ? SettlementPayee.BRAND : SettlementPayee.CREATOR;
        String raw = aml.get(payee);
        return raw == null ? Optional.empty() : Optional.of(new AmlState(raw, isAmlBlocking(raw)));
    }

    @Override
    public boolean isAmlBlocking(String rawAmlStatus) {
        return inicisRules.isAmlBlocking(rawAmlStatus);
    }

    public static String externalIdOf(PartnerProfile profile) {
        return profile.externalId() != null ? profile.externalId() : "FAKE-SELLER-" + profile.partnerRef();
    }

    /** 이니시스 흉내 — 이 수취자의 {@code amlstatus} 원시값({@link #AML_VALID} · {@link #AML_EXPIRED}). null 이면 조회 불가(empty). */
    public synchronized void amlFor(SettlementPayee payee, String raw) {
        if (raw == null) {
            aml.remove(payee);
        } else {
            aml.put(payee, raw);
        }
    }

    /** 이 수취자의 등록이 사유와 함께 실패한다(PG 에 셀러가 생기지 않았다) — 다음 {@link #reset()} · {@link #approveFor} 전까지. */
    public synchronized void failFor(SettlementPayee payee, String code, String reason) {
        scripted.put(payee, PartnerResult.failed(code, reason));
    }

    /** 등록은 됐지만 대기 — 토스 {@code APPROVAL_REQUIRED}(본인인증) 또는 {@code KYC_REQUIRED}. id 는 발급된다. */
    public synchronized void pendingFor(SettlementPayee payee, String rawStatus) {
        scripted.put(payee, PartnerResult.pending(null, rawStatus, blockCodeOf(rawStatus),
                KYC_REQUIRED.equals(rawStatus) ? "토스 KYC 심사 대기" : "토스 본인인증 대기"));
    }

    /** 승인 상태를 정해 돌려준다({@code PARTIALLY_APPROVED} 기본 · {@code APPROVED} 는 한도 없음). */
    public synchronized void approveFor(SettlementPayee payee, String rawStatus) {
        scripted.put(payee, PartnerResult.approved(null, rawStatus));
    }

    public synchronized void approveFor(SettlementPayee payee) {
        scripted.remove(payee);
    }

    /**
     * 다음 {@link #ensure} 한 번이 PG 를 부르는 동안 끼어들 일 — PG 응답을 기다리는 사이 회원 정보가 또 바뀌는 경합 · PG 호출 예외를 재현한다.
     * 한 번 쓰면 사라진다.
     */
    public synchronized void beforeNextEnsure(Runnable hook) {
        beforeNextEnsure = hook;
    }

    public synchronized List<PartnerProfile> calls() {
        return List.copyOf(calls);
    }

    public synchronized List<PartnerProfile> calls(SettlementPayee payee) {
        return calls.stream().filter(p -> p.payee() == payee).toList();
    }

    public synchronized void reset() {
        scripted.clear();
        aml.clear();
        calls.clear();
        env = "TEST";
        inicis = false;
        beforeNextEnsure = null;
    }
}
