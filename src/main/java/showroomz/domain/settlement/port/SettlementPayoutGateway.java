package showroomz.domain.settlement.port;

import showroomz.domain.settlement.type.SettlementPayee;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.Optional;

/**
 * 지급 포트(44 어드민 설계서 0-5 · 3-3 · 60_KG이니시스_지급대행_전환_계획서.md 4-1) — <b>묶음 지급</b>({@code SIMULATED} · 이니시스).
 * 같은 수취자 · 지급일의 행을 도메인이 묶어 합계 1건으로 올린다({@link #submitGroup} · D-1). 합계 · 잠금 · 기록은 도메인
 * {@code SettlementPayoutGroupService} 한 곳이 하고, 어댑터는 상태가 없다. 결과는 {@link #lookupGroups}(폴링 1차 · D-8).
 */
public interface SettlementPayoutGateway {

    String PROVIDER_SIMULATED = "SIMULATED";
    String PROVIDER_INICIS = "INICIS";
    /** 접수(REQUESTED)됐지만 PG 가 고객확인(AML) 대상이라고 알렸다 — {@link GroupSubmitResult#failCode} 에 실린다(이니시스 SB05 · D-6). */
    String NOTICE_AML_REQUIRED = "AML_REQUIRED";

    /** 어느 PG 인가 — {@code SIMULATED | INICIS}. 지급 묶음 · 수취자 행의 {@code pg_provider} 에 남는다(D-12). */
    default String provider() {
        return PROVIDER_SIMULATED;
    }

    /** 바깥 PG 로 돈을 보내는가 — 시뮬레이터만 false. 수취자 행의 {@code pg_provider} 는 이때만 적는다. */
    default boolean isExternal() {
        return !PROVIDER_SIMULATED.equals(provider());
    }

    // ------------------------------------------------------------------ 묶음 지급(60 전환 계획서 4-1)

    /** 예정일보다 며칠(영업일) 먼저 지시하는가(D-2) — 이니시스 1 · 그 밖 0(예정일 당일 지급). */
    default int leadBusinessDays() {
        return 0;
    }

    /**
     * 묶음 수정 · 재업로드 마감 시각(KST · 업로드일 기준) — 이니시스 익일지급 15:00. empty 면 묶음이 올리는 즉시 확정된다({@code SIMULATED}) —
     * 마감 전 틱의 수렴 · 빼기(D-1 ④ · D-7)가 없다.
     */
    default Optional<LocalTime> uploadCutoff() {
        return Optional.empty();
    }

    /** 묶음의 차수 — 이니시스 {@code NEXT_DAY}(clService 0) · 시뮬레이터 {@code IMMEDIATE}. */
    default String scheduleType() {
        return "IMMEDIATE";
    }

    /** 14:00 재검사에도 잔액이 모자라면 큰 묶음부터 남기고 나머지를 지우는가(D-9). */
    default boolean trimOnShortfall() {
        return true;
    }

    /**
     * 묶음의 현재 합계로 올린다 — {@code total > 0} 이면 등록(덮어쓰기) · 0 이면 삭제. 합계는 도메인이 계산한다(어댑터는 상태가 없다 ·
     * 「새로 붙은 행만」 보면 덮어쓰기로 앞의 금액을 지운다). <b>예외 = 결과 모름</b> — 도메인은 확정 합계를 바꾸지 않고 마감 전 틱이 다시 올린다.
     */
    GroupSubmitResult submitGroup(GroupCommand command);

    /** 지급일이 지난 묶음의 송금 결과 — 돌려주지 않은 묶음은 그대로 OPEN 이다. 예외 = 조회 실패(다음 폴링이 다시). */
    default List<GroupResult> lookupGroups(List<GroupLookup> lookups) {
        return List.of();
    }

    /**
     * @param partnerExternalId PG 서브몰 id(이니시스 {@code idMall}) — 시뮬레이터는 null
     * @param total             묶음에 속한 REQUESTED 행 전부의 합 — 0 이면 삭제
     */
    record GroupCommand(Long groupId, SettlementPayee payee, Long payeeRefId, String partnerExternalId, LocalDate payDate,
                        String scheduleType, long total) {
    }

    /**
     * @param outcome   {@code REQUESTED}(접수) · {@code PAID}(시뮬레이터 · 즉시 완료) · {@code FAILED}(확정 거절) · {@code DEFERRED}(잔액 부족) ·
     *                  {@code RETRY}(일시 오류 · 접수 안 됨 — 다음 틱이 다시)
     * @param requestId PG 추적 id(이니시스 {@code X-Request-ID}) — 묶음에 남겨 문의 때 쓴다
     */
    record GroupSubmitResult(Outcome outcome, String failCode, String failReason, String requestId) {

        public static GroupSubmitResult requested(String requestId) {
            return new GroupSubmitResult(Outcome.REQUESTED, null, null, requestId);
        }
    }

    /** @param confirmedAmount PG 가 받은 마지막 합계 — 송금 결과 대조 기준 */
    record GroupLookup(Long groupId, SettlementPayee payee, String partnerExternalId, LocalDate payDate, String scheduleType,
                       long confirmedAmount) {
    }

    /**
     * @param outcome    {@code PAID} · {@code FAILED} · {@code REQUESTED}(아직)
     * @param remittedOn 송금일(이니시스 {@code remitDtPay}) — 원천징수영수증의 지급일
     * @param pgAmount   PG 가 보낸 금액 — 확정 합계와 다르면 도메인이 닫지 않고 사람을 부른다
     * @param remittedTo PG 가 실제로 보낸 계좌(은행 코드 · 계좌번호 끝 4자리) — 모르면 null. 묶음 행의 계좌 스냅샷과 다르면 지급은 닫되 사람을 부른다
     */
    record GroupResult(Long groupId, Outcome outcome, LocalDate remittedOn, String failCode, String failReason, long pgAmount,
                       RemittedAccount remittedTo) {

        public GroupResult(Long groupId, Outcome outcome, LocalDate remittedOn, String failCode, String failReason, long pgAmount) {
            this(groupId, outcome, remittedOn, failCode, failReason, pgAmount, null);
        }
    }

    /**
     * 송금 결과에 실린 계좌 — 평문 계좌번호는 들고 다니지 않는다(끝 4자리만). 이니시스 {@code payresult} 의 {@code cdBank} · {@code noAcct}.
     *
     * @param bankCode    은행 코드(3자리) · 모르면 null
     * @param accountTail 계좌번호 숫자 끝 4자리 · 모르면 null
     */
    record RemittedAccount(String bankCode, String accountTail) {

        public static final int TAIL = 4;

        /** 은행 코드 · 계좌번호(마스킹 문자가 섞여도 숫자만 본다)에서 — 둘 다 없으면 null. */
        public static RemittedAccount of(String bankCode, String accountNumber) {
            String code = bankCode == null || bankCode.isBlank() ? null : bankCode.trim();
            String digits = accountNumber == null ? "" : accountNumber.replaceAll("[^0-9]", "");
            String tail = digits.length() < TAIL ? null : digits.substring(digits.length() - TAIL);
            return code == null && tail == null ? null : new RemittedAccount(code, tail);
        }
    }

    /** 지급 가능 잔액(D-9) — 지원하지 않는 모드는 empty(선검사 생략). */
    default Optional<Long> availableBalance() {
        return Optional.empty();
    }

    /**
     * {@code DEFERRED} — 잔액 부족 · 지시를 되돌려 다음 지급일로(D-9). attempt 를 소모하지 않는다.
     * {@code RETRY} — PG 가 이번 요청을 처리하지 못했다고 답했다(서버 일시 오류 · 요청 id 중복 등 — 접수되지 않은 것은 확실하다).
     * 행을 그대로 두고 마감 전 틱이 같은 합계로 다시 올린다. 마감이 지나면 받은 적 없는 행은 다음 지급일로 넘어간다(attempt 불변 · 운영자 재분배 불필요).
     */
    enum Outcome { PAID, REQUESTED, FAILED, DEFERRED, RETRY }
}
