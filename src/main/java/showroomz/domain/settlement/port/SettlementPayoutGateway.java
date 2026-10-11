package showroomz.domain.settlement.port;

import showroomz.domain.settlement.type.SettlementPayee;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

/**
 * 지급 포트(44 어드민 설계서 0-5 · 3-3). {@code SIMULATED} 는 즉시 PAID, 포트원({@code PG_TEST · PG})은 정산건을 올리고
 * REQUESTED 로 둔 뒤 {@link #lookup} 이 닫는다(44_포트원_파트너정산_연동_BE_설계서.md 5절).
 */
public interface SettlementPayoutGateway {

    /** 수취자별 지급 지시. 즉시 결과가 없는 PG 는 REQUESTED 로 두고 {@code SettlementPayoutResultScheduler}가 닫는다. */
    PayoutResult distribute(PayoutCommand command);

    /** REQUESTED 행의 결과 — PG 가 비동기인 모드만 구현한다. 돌려주지 않은 행은 그대로 REQUESTED 다. */
    default List<LineResult> lookup(List<PayoutLookup> lookups) {
        return List.of();
    }

    /** 지시 뒤 대조(5-3) — 그 정산일에 PG 에 올라간 정산건 수 · 합계. 지원하지 않는 모드는 empty. */
    default Optional<Reconciliation> reconcile(LocalDate settlementDate) {
        return Optional.empty();
    }

    record PayoutCommand(Long settlementId, String settlementNumber, List<PayoutLine> lines) {
    }

    /**
     * @param accountNumber 평문 — 지시 순간에만 복호화해 넘긴다. 플랫폼 행은 내부 계정이라 null
     * @param payeeRefId    BRAND = marketId · CREATOR = creatorId · PLATFORM = null(파트너 id 산출)
     * @param attempt       재분배 횟수 — 정산건 id 의 꼬리(같은 행 재지시는 같은 id · 재분배는 새 id)
     * @param bankCode      표준 은행 코드 — 은행 이름을 되돌릴 수 없으면 null(PG 가 계좌를 바꿔야 할 때 거절한다)
     */
    record PayoutLine(Long payoutId, SettlementPayee payee, long amount, String bankName, String accountNumber,
                      String holder, Long payeeRefId, int attempt, String bankCode) {

        public PayoutLine(Long payoutId, SettlementPayee payee, long amount, String bankName, String accountNumber,
                          String holder) {
            this(payoutId, payee, amount, bankName, accountNumber, holder, null, 0, null);
        }

        public PayoutLine(Long payoutId, SettlementPayee payee, long amount, String bankName, String accountNumber,
                          String holder, Long payeeRefId, int attempt) {
            this(payoutId, payee, amount, bankName, accountNumber, holder, payeeRefId, attempt, null);
        }
    }

    record PayoutResult(List<LineResult> lines) {
    }

    /**
     * @param pgReference  수취자에게 보이는 참조번호 — 포트원은 지급 id(PAID 때) · 시뮬레이터는 SIM-…
     * @param pgTransferId PG 정산건 id(REQUESTED 때 기록) — 결과 조회의 키
     */
    record LineResult(Long payoutId, Outcome outcome, String pgReference, String failCode, String failReason,
                      String pgTransferId) {

        public LineResult(Long payoutId, Outcome outcome, String pgReference, String failCode, String failReason) {
            this(payoutId, outcome, pgReference, failCode, failReason, null);
        }
    }

    /** 결과 조회 입력 — 정산건 id 가 기록되지 않았어도(지시 중 통신 실패) 번호 · 수취자 · 회차로 다시 계산할 수 있다. */
    record PayoutLookup(Long payoutId, Long settlementId, String settlementNumber, SettlementPayee payee, long amount,
                        int attempt, String pgTransferId, String pgPayoutId) {
    }

    record Reconciliation(int count, long amount) {
    }

    enum Outcome { PAID, REQUESTED, FAILED }
}
