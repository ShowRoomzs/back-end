package showroomz.domain.settlement.port;

import showroomz.domain.settlement.type.SettlementPayee;

import java.util.List;

/**
 * 3자 분배 지급 포트(44 어드민 설계서 0-5 · 3-3) — 정산은 PG 에 수취자별 지급을 지시하고 결과(성공 · 실패 · PG 참조번호)를 받는다.
 * 플랫폼이 대금을 보관하지 않는다(§41-5). 구현은 {@code settlement.payout.mode}로 고른다 — {@code SIMULATED}(즉시 지급 완료 ·
 * 개발 · QA · 통합 테스트) / {@code PG}(지급대행 — 미구현 · 런칭 게이트 · 13절 A-1).
 */
public interface SettlementPayoutGateway {

    /** 수취자별 지급 지시. 즉시 결과가 없는 PG 는 REQUESTED 로 두고 {@code SettlementPayoutResultScheduler}가 닫는다. */
    PayoutResult distribute(PayoutCommand command);

    record PayoutCommand(Long settlementId, String settlementNumber, List<PayoutLine> lines) {
    }

    /** @param accountNumber 평문 — 지시 순간에만 복호화해 넘긴다. 플랫폼 행은 내부 계정이라 null */
    record PayoutLine(Long payoutId, SettlementPayee payee, long amount, String bankName, String accountNumber,
                      String holder) {
    }

    record PayoutResult(List<LineResult> lines) {
    }

    record LineResult(Long payoutId, Outcome outcome, String pgReference, String failCode, String failReason) {
    }

    enum Outcome { PAID, REQUESTED, FAILED }
}
