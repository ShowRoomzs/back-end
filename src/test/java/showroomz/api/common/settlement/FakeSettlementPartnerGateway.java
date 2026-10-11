package showroomz.api.common.settlement;

import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Component;
import showroomz.domain.settlement.port.SettlementPartnerGateway;
import showroomz.domain.settlement.type.SettlementPayee;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/**
 * 테스트용 파트너 포트(포트원 설계서 8-1) — 기본은 즉시 승인. 수취자별 실패 사유 · 심사 중을 심고, 호출된 프로필을 기록한다.
 * 컨텍스트 공용 빈이라 테스트마다 {@link #reset()}한다.
 */
@Primary
@Component
public class FakeSettlementPartnerGateway implements SettlementPartnerGateway {

    private final Map<SettlementPayee, PartnerResult> scripted = new EnumMap<>(SettlementPayee.class);
    private final List<PartnerProfile> calls = new ArrayList<>();

    @Override
    public synchronized PartnerResult ensure(PartnerProfile profile) {
        calls.add(profile);
        PartnerResult script = scripted.get(profile.payee());
        return script == null ? PartnerResult.approved() : script;
    }

    /** 이 수취자의 등록이 사유와 함께 실패한다 — 다음 {@link #reset()} · {@link #approveFor} 전까지. */
    public synchronized void failFor(SettlementPayee payee, String code, String reason) {
        scripted.put(payee, PartnerResult.failed(code, reason));
    }

    /** 포트원이 심사 중(PENDING)으로 돌려준다. */
    public synchronized void pendingFor(SettlementPayee payee) {
        scripted.put(payee, new PartnerResult(PartnerStatus.PENDING, "PARTNER_NOT_READY", "심사 중"));
    }

    public synchronized void approveFor(SettlementPayee payee) {
        scripted.remove(payee);
    }

    public synchronized List<PartnerProfile> calls() {
        return List.copyOf(calls);
    }

    public synchronized List<PartnerProfile> calls(SettlementPayee payee) {
        return calls.stream().filter(p -> p.payee() == payee).toList();
    }

    public synchronized void reset() {
        scripted.clear();
        calls.clear();
    }
}
