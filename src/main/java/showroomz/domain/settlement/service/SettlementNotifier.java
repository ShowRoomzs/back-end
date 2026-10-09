package showroomz.domain.settlement.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import showroomz.domain.settlement.type.SettlementPayee;

/**
 * 정산 알림 — <b>훅 자리만 두고 발송은 스텁</b>이다(44 어드민 설계서 · 이슈 스레드 설계서 8-2). {@code GroupBuyNotifier}와 같다.
 *
 * <p>채널(메일 / 웹 알림센터)이 정해지지 않았다. 정산 · 조정 이벤트를 전부 이 클래스 하나에 두고 호출 지점만 박는다 — 채널이
 * 정해지면 이 클래스 안쪽만 채운다. 운영자에게 보내는 것은 조치가 필요한 사건(분배 실패 · 세금계산서 대조 대기)뿐이다.
 */
@Slf4j
@Component
public class SettlementNotifier {

    // ------------------------------------------------------------------ 정산

    /** 생성 — 양측 「{MM.DD} 23:59까지 확인 · 조정 요청 가능」. */
    public void settlementGenerated(Long settlementId) {
        stub("BOTH", "SETTLEMENT_GENERATED", settlementId);
    }

    /** 확정 — 양측 지급 예정일 · 사업자 「세금계산서 발행 요청」. */
    public void settlementConfirmed(Long settlementId) {
        stub("BOTH", "SETTLEMENT_CONFIRMED", settlementId);
    }

    /** 수취자 몫 지급 완료 — 수취자별. */
    public void settlementPaid(Long settlementId, SettlementPayee payee) {
        stub(payee.name(), "SETTLEMENT_PAID", settlementId);
    }

    /** 분배 실패 — 운영자(재분배 M3). */
    public void payoutFailed(Long settlementId, SettlementPayee payee) {
        stub("ADMIN", "PAYOUT_FAILED:" + payee.name(), settlementId);
    }

    public void taxInvoiceSubmitted(Long settlementId) {
        stub("ADMIN", "TAX_INVOICE_SUBMITTED", settlementId);
    }

    public void taxInvoiceVerified(Long settlementId) {
        stub("CREATOR", "TAX_INVOICE_VERIFIED", settlementId);
    }

    public void taxInvoiceRejected(Long settlementId) {
        stub("CREATOR", "TAX_INVOICE_REJECTED", settlementId);
    }

    /** 차감 발생 — 해당 측 「다음 정산에서 {amount}원이 차감됩니다」(§41-7 사전 고지). */
    public void clawbackRegistered(Long originSettlementId, String clawbackNumber) {
        stub("BOTH", "CLAWBACK_REGISTERED:" + clawbackNumber, originSettlementId);
    }

    // ------------------------------------------------------------------ 조정 협의(이슈 스레드 설계서 8-2)

    public void adjustmentRequested(Long settlementId, String to) {
        stub(to, "ADJUSTMENT_REQUESTED", settlementId);
    }

    public void adjustmentCountered(Long settlementId, String to) {
        stub(to, "ADJUSTMENT_COUNTERED", settlementId);
    }

    public void adjustmentRejected(Long settlementId, String to) {
        stub(to, "ADJUSTMENT_REJECTED", settlementId);
    }

    public void adjustmentAgreed(Long settlementId) {
        stub("BOTH", "ADJUSTMENT_AGREED", settlementId);
    }

    public void adjustmentDeadlineD1(Long settlementId) {
        stub("BOTH", "ADJUSTMENT_DEADLINE_D1", settlementId);
    }

    public void adjustmentExpired(Long settlementId) {
        stub("BOTH", "ADJUSTMENT_EXPIRED", settlementId);
    }

    private static void stub(String to, String event, Long settlementId) {
        log.info("[settlement-notify:stub] to={} event={} settlementId={}", to, event, settlementId);
    }
}
