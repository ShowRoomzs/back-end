package showroomz.domain.settlement.port;

import showroomz.domain.settlement.type.SettlementPayee;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

/**
 * 지급 포트(44 어드민 설계서 0-5 · 3-3). {@code SIMULATED} 는 즉시 PAID, 토스({@code PG_TEST · PG})는 지급을 요청하고 REQUESTED 로 둔 뒤
 * 웹훅({@link #fromWebhook}) · 폴링({@link #lookup}) 이 닫는다(44_토스페이먼츠_지급대행_연동_BE_설계서.md 5절).
 */
public interface SettlementPayoutGateway {

    /** 수취자별 지급 지시. 즉시 결과가 없는 PG 는 REQUESTED 로 두고 결과 경로가 닫는다. */
    PayoutResult distribute(PayoutCommand command);

    /** REQUESTED 행의 결과 — PG 가 비동기인 모드만 구현한다. 돌려주지 않은 행은 그대로 REQUESTED 다. */
    default List<LineResult> lookup(List<PayoutLookup> lookups) {
        return List.of();
    }

    /** 지시 뒤 대사(5-3) — 그 지급일에 PG 에 올라간 건수 · 합계. 지원하지 않는 모드는 empty. */
    default Optional<Reconciliation> reconcile(LocalDate payoutDate) {
        return Optional.empty();
    }

    /** 지급 가능 잔액(5-1 ①) — 지원하지 않는 모드는 empty(선검사 생략). */
    default Optional<Long> availableBalance() {
        return Optional.empty();
    }

    /** 검증 끝난 웹훅의 지급 객체(JSON) → 결과. 폴링과 같은 전이를 탄다(5-4). 해석 못 하면 empty. */
    default Optional<LineResult> fromWebhook(String payoutJson, PayoutLookup lookup) {
        return Optional.empty();
    }

    /** @param requestedAt 지시 시각 — 어댑터가 당일(EXPRESS) · 예약(SCHEDULED) 을 가른다(0-4) */
    record PayoutCommand(Long settlementId, String settlementNumber, List<PayoutLine> lines, LocalDateTime requestedAt) {

        public PayoutCommand(Long settlementId, String settlementNumber, List<PayoutLine> lines) {
            this(settlementId, settlementNumber, lines, LocalDateTime.now());
        }
    }

    /**
     * @param accountNumber     평문 — 지시 순간에만 복호화해 넘긴다. 플랫폼 행은 내부 계정이라 null
     * @param payeeRefId        BRAND = marketId · CREATOR = creatorId · PLATFORM = null(참조 id 산출)
     * @param attempt           재분배 횟수 — 지급 참조 id 의 꼬리(같은 행 재지시는 같은 ref · 재분배는 새 ref)
     * @param bankCode          표준 은행 코드 — 은행 이름을 되돌릴 수 없으면 null(PG 가 계좌를 바꿔야 할 때 거절한다)
     * @param partnerExternalId PG 발급 셀러 id — 회원 정보의 연결 · 없으면 null(확정 때 보장했어야 한다 · 5-2)
     */
    record PayoutLine(Long payoutId, SettlementPayee payee, long amount, String bankName, String accountNumber,
                      String holder, Long payeeRefId, int attempt, String bankCode, String partnerExternalId) {

        public PayoutLine(Long payoutId, SettlementPayee payee, long amount, String bankName, String accountNumber,
                          String holder) {
            this(payoutId, payee, amount, bankName, accountNumber, holder, null, 0, null, null);
        }

        public PayoutLine(Long payoutId, SettlementPayee payee, long amount, String bankName, String accountNumber,
                          String holder, Long payeeRefId, int attempt) {
            this(payoutId, payee, amount, bankName, accountNumber, holder, payeeRefId, attempt, null, null);
        }
    }

    record PayoutResult(List<LineResult> lines) {
    }

    /**
     * @param pgReference  수취자에게 보이는 참조번호 — 토스는 지급 id(REQUESTED 때부터) · 시뮬레이터는 SIM-…
     * @param pgTransferId PG 지급 참조 id({@code refPayoutId} · REQUESTED 때 기록) — 결과 조회의 키
     * @param scheduleType {@code EXPRESS | SCHEDULED}(토스) · 그 밖은 null
     * @param payoutDate   PG 지급일 — SCHEDULED 면 수취자 화면 「{날짜} 지급 예정」
     */
    record LineResult(Long payoutId, Outcome outcome, String pgReference, String failCode, String failReason,
                      String pgTransferId, String scheduleType, LocalDate payoutDate) {

        public LineResult(Long payoutId, Outcome outcome, String pgReference, String failCode, String failReason) {
            this(payoutId, outcome, pgReference, failCode, failReason, null, null, null);
        }

        public LineResult(Long payoutId, Outcome outcome, String pgReference, String failCode, String failReason,
                          String pgTransferId) {
            this(payoutId, outcome, pgReference, failCode, failReason, pgTransferId, null, null);
        }
    }

    /** 결과 조회 입력 — 참조 id 가 기록되지 않았어도(지시 중 통신 실패) 번호 · 수취자 · 회차로 다시 계산할 수 있다. */
    record PayoutLookup(Long payoutId, Long settlementId, String settlementNumber, SettlementPayee payee, long amount,
                        int attempt, String pgTransferId, String pgPayoutId, LocalDateTime requestedAt) {

        public PayoutLookup(Long payoutId, Long settlementId, String settlementNumber, SettlementPayee payee, long amount,
                            int attempt, String pgTransferId, String pgPayoutId) {
            this(payoutId, settlementId, settlementNumber, payee, amount, attempt, pgTransferId, pgPayoutId, null);
        }
    }

    record Reconciliation(int count, long amount) {
    }

    /** {@code DEFERRED} — 잔액 부족 · 지시를 되돌려 다음 영업일로(5-1 ①). attempt 를 소모하지 않는다. */
    enum Outcome { PAID, REQUESTED, FAILED, DEFERRED }
}
