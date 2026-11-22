package showroomz.global.payment.settlement.inicis;

import lombok.extern.slf4j.Slf4j;
import showroomz.domain.settlement.port.SettlementPayoutGateway;
import showroomz.domain.settlement.port.SettlementPayoutGateway.RemittedAccount;
import showroomz.global.config.properties.InicisPayoutsProperties;
import showroomz.global.payment.portone.PaymentGatewayRejectedException;
import showroomz.global.payment.settlement.inicis.InicisPayoutsDtos.PayCheck;
import showroomz.global.payment.settlement.inicis.InicisPayoutsDtos.PayRequest;
import showroomz.global.payment.settlement.inicis.InicisPayoutsDtos.PayResult;
import showroomz.global.utils.BusinessCalendar;
import showroomz.global.utils.KstDates;

import java.time.LocalDate;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * 이니시스 지급대행 어댑터(60_KG이니시스_지급대행_전환_계획서.md 5절) — 묶음 지급. 상태가 없다: 합계는 도메인이 계산해 넘기고, 이 어댑터는
 * {@code payreq} 를 합계로 {@code INS}(0원이면 {@code DEL}) 할 뿐이다(같은 키로 다시 올리면 덮어쓴다 · Q3).
 *
 * <ul>
 *   <li>지시는 지급일 전 영업일(리드 1) · 익일지급({@code clService=0}) · 수정 마감 15:00(D-2). 보내는 {@code dtPay} 는
 *   {@code dt-pay-basis} 로 고른다 — {@code PAYMENT_DATE}(돈이 나가는 날) / {@code UPLOAD_DATE}(전 영업일 · Q2).</li>
 *   <li>결과는 {@code payresult} 를 <b>지급일 하루씩</b> 묻는다(같은 날짜 묶음 여럿 → 1회 · D-8). {@code clPay=01} 이면 PAID · 그 밖 FAILED
 *   ({@code INICIS_{clPay}}). 금액은 그대로 돌려준다 — 확정 합계와 다르면 도메인이 닫지 않는다.</li>
 *   <li>통신 실패 · 5xx 는 예외(결과 모름)로 그대로 던진다 — 도메인이 확정 합계를 바꾸지 않고 마감 전 틱이 다시 올린다.</li>
 *   <li>{@code PG_TEST}(스테이징)는 결과 · 잔액 조회가 막혀 있다(1-8) — 잔액은 empty, 결과는 {@code test.assume-paid} 일 때만 지급일이 지난 묶음을
 *   PAID 로 돌려준다(D-10).</li>
 * </ul>
 */
@Slf4j
public class InicisSettlementPayoutGateway implements SettlementPayoutGateway {

    public static final String SCHEDULE_NEXT_DAY = "NEXT_DAY";
    private static final DateTimeFormatter DATE = DateTimeFormatter.BASIC_ISO_DATE;

    private final InicisPayoutsClient client;
    private final boolean test;
    private final InicisPayoutsProperties properties;
    private final BusinessCalendar businessCalendar;

    public InicisSettlementPayoutGateway(InicisPayoutsClient client, boolean test, InicisPayoutsProperties properties,
                                         BusinessCalendar businessCalendar) {
        this.client = client;
        this.test = test;
        this.properties = properties;
        this.businessCalendar = businessCalendar;
    }

    @Override
    public String provider() {
        return PROVIDER_INICIS;
    }

    @Override
    public int leadBusinessDays() {
        return 1;
    }

    @Override
    public Optional<LocalTime> uploadCutoff() {
        return Optional.of(properties.getUploadCutoff());
    }

    @Override
    public String scheduleType() {
        return SCHEDULE_NEXT_DAY;
    }

    @Override
    public boolean trimOnShortfall() {
        return properties.isTrimOnShortfall();
    }

    // ------------------------------------------------------------------ 잔액(D-9)

    @Override
    public Optional<Long> availableBalance() {
        if (test) {
            return Optional.empty();
        }
        LocalDate today = KstDates.today();
        PayCheck check = client.payCheck(today, today).body();
        if (check == null || check.totalAmt() == null || check.totalAmt().isBlank()) {
            return Optional.empty();
        }
        return Optional.of(parseAmount(check.totalAmt()));
    }

    // ------------------------------------------------------------------ 지시(1-3)

    @Override
    public GroupSubmitResult submitGroup(GroupCommand command) {
        if (command.partnerExternalId() == null || command.partnerExternalId().isBlank()) {
            return new GroupSubmitResult(Outcome.FAILED, InicisPayoutFailureClassifier.PARTNER_NOT_READY,
                    "PG 서브몰 미연결", null);
        }
        boolean delete = command.total() <= 0;
        PayRequest request = new PayRequest(client.merchantId(), command.partnerExternalId(), dtPay(command.payDate()),
                String.valueOf(Math.max(command.total(), 0)), InicisPayoutsDtos.CL_SERVICE_NEXT_DAY,
                delete ? InicisPayoutsDtos.DML_DELETE : InicisPayoutsDtos.DML_INSERT, InicisPayoutsDtos.SVC_REQUEST);
        try {
            InicisPayoutsClient.Reply<Void> reply = client.payRequest(request);
            if (InicisPayoutFailureClassifier.PAYREQ_AML_REQUIRED.equals(reply.code())) {
                // SB05 — 접수는 됐다. 고객확인 대상이라 안내한다(D-6 · 정산 제한 여부는 송금 결과가 가른다).
                return new GroupSubmitResult(Outcome.REQUESTED, NOTICE_AML_REQUIRED, "고객확인이행 대상(SB05)", reply.requestId());
            }
            return GroupSubmitResult.requested(reply.requestId());
        } catch (PaymentGatewayRejectedException e) {
            if (InicisPayoutFailureClassifier.isTransient(e.getType())) {
                // 이니시스 서버 일시 오류(EM18 · EM08 등) · 요청 id 중복 — 접수 안 됨. 행을 그대로 두고 마감 전 틱이 다시 올린다.
                log.warn("이니시스 지급 요청 일시 오류 — 다음 틱 재전송 - groupId: {}, idMall: {}, code: {}", command.groupId(),
                        command.partnerExternalId(), e.getType());
                return new GroupSubmitResult(Outcome.RETRY, e.getType(), e.getType() + " · " + e.getMessage(), null);
            }
            if (!delete && InicisPayoutFailureClassifier.isInsufficientBalance(e.getType())) {
                return new GroupSubmitResult(Outcome.DEFERRED, InicisPayoutFailureClassifier.INSUFFICIENT_BALANCE,
                        e.getMessage(), null);
            }
            String code = InicisPayoutFailureClassifier.payRequestFailCode(e.getType());
            log.error("이니시스 지급 요청 거절 - groupId: {}, idMall: {}, code: {}", command.groupId(),
                    command.partnerExternalId(), e.getType());
            return new GroupSubmitResult(Outcome.FAILED, code, e.getType() + " · " + e.getMessage(), null);
        }
    }

    /** 보내는 {@code dtPay} — 돈이 나가는 날(기본) 또는 업로드 기준일(전 영업일 · Q2). */
    String dtPay(LocalDate payDate) {
        LocalDate value = properties.getDtPayBasis() == InicisPayoutsProperties.DtPayBasis.UPLOAD_DATE
                ? businessCalendar.previousBusinessDay(payDate) : payDate;
        return value.format(DATE);
    }

    // ------------------------------------------------------------------ 결과(1-5 · D-8)

    @Override
    public List<GroupResult> lookupGroups(List<GroupLookup> lookups) {
        if (lookups.isEmpty()) {
            return List.of();
        }
        if (test) {
            return assumePaid(lookups);
        }
        Map<String, List<GroupLookup>> byDate = new LinkedHashMap<>();
        for (GroupLookup lookup : lookups) {
            byDate.computeIfAbsent(dtPay(lookup.payDate()), d -> new ArrayList<>()).add(lookup);
        }
        List<GroupResult> results = new ArrayList<>();
        for (Map.Entry<String, List<GroupLookup>> entry : byDate.entrySet()) {
            LocalDate date = LocalDate.parse(entry.getKey(), DATE);
            List<PayResult> remitted;
            try {
                remitted = client.payResults(date, date, InicisPayoutsDtos.CL_SERVICE_NEXT_DAY).body();
            } catch (RuntimeException e) {
                // 한 날짜의 조회 실패가 다른 날짜의 결과를 막지 않는다 — 다음 폴링이 다시 묻는다.
                log.error("이니시스 송금 결과 조회 실패 - dtPay: {}", entry.getKey(), e);
                continue;
            }
            Map<String, List<PayResult>> byMall = new LinkedHashMap<>();
            for (PayResult r : remitted) {
                if (r.idMall() != null && (r.dtPay() == null || entry.getKey().equals(r.dtPay().trim()))) {
                    byMall.computeIfAbsent(r.idMall().trim(), k -> new ArrayList<>()).add(r);
                }
            }
            for (GroupLookup lookup : entry.getValue()) {
                List<PayResult> found = byMall.get(lookup.partnerExternalId());
                if (found == null || found.isEmpty()) {
                    continue;   // 아직 결과 없음 — 묶음은 그대로 OPEN
                }
                results.add(toResult(lookup, found));
            }
        }
        return results;
    }

    private GroupResult toResult(GroupLookup lookup, List<PayResult> found) {
        if (found.size() > 1) {
            log.warn("이니시스 송금 결과가 한 키에 여럿 - idMall: {}, dtPay: {}, count: {}", lookup.partnerExternalId(),
                    lookup.payDate(), found.size());
        }
        // 한 키에 결과가 여럿이면(같은 키 재등록이 덮어쓰기가 아니다 · Q3) 「실제로 나간 돈」만 지급으로 본다 — 하나라도 실패라고 묶음 전체를 FAILED 로
        // 닫으면 성공한 몫까지 재분배가 한 번 더 보낸다. 전부 실패일 때만 FAILED. 성공 합계가 확정 합계와 다르면 도메인이 닫지 않고 사람을 부른다.
        List<PayResult> succeeded = found.stream().filter(r -> InicisPayoutsDtos.CL_PAY_SUCCESS.equals(trim(r.clPay()))).toList();
        if (succeeded.isEmpty()) {
            PayResult failed = found.get(0);
            String code = InicisPayoutFailureClassifier.payResultFailCode(trim(failed.clPay()));
            long amount = found.stream().mapToLong(r -> parseAmount(r.amtSupply())).sum();
            return new GroupResult(lookup.groupId(), Outcome.FAILED, remittedOn(found, lookup), code,
                    "이니시스 송금 실패 clPay=" + trim(failed.clPay()), amount);
        }
        if (succeeded.size() < found.size()) {
            log.warn("이니시스 송금 결과 성공 · 실패 섞임 — 성공 합계만 지급으로 본다 - idMall: {}, dtPay: {}, ok: {}/{}",
                    lookup.partnerExternalId(), lookup.payDate(), succeeded.size(), found.size());
        }
        long amount = succeeded.stream().mapToLong(r -> parseAmount(r.amtSupply())).sum();
        // 실제로 보낸 계좌 — 성공 결과의 계좌가 하나일 때만 싣는다(갈리면 대조하지 않는다 · 금액 대조가 사람을 부른다).
        List<RemittedAccount> accounts = succeeded.stream().map(r -> RemittedAccount.of(r.cdBank(), r.noAcct()))
                .filter(java.util.Objects::nonNull).distinct().toList();
        RemittedAccount remittedTo = accounts.size() == 1 ? accounts.get(0) : null;
        return new GroupResult(lookup.groupId(), Outcome.PAID, remittedOn(succeeded, lookup), null, null, amount, remittedTo);
    }

    private static LocalDate remittedOn(List<PayResult> rows, GroupLookup lookup) {
        return rows.stream().map(r -> parseDate(r.remitDtPay())).filter(java.util.Objects::nonNull).findFirst()
                .orElse(lookup.payDate());
    }

    /** 스테이징 — 송금 결과를 볼 수 없다. {@code assume-paid} 면 지급일이 된 묶음을 확정 합계 그대로 PAID 로(D-10). */
    private List<GroupResult> assumePaid(List<GroupLookup> lookups) {
        if (!properties.getTest().isAssumePaid()) {
            return List.of();
        }
        LocalDate today = KstDates.today();
        return lookups.stream().filter(l -> !l.payDate().isAfter(today))
                .map(l -> new GroupResult(l.groupId(), Outcome.PAID, l.payDate(), null, null, l.confirmedAmount()))
                .toList();
    }

    private static long parseAmount(String value) {
        if (value == null) {
            return -1L;
        }
        String digits = value.replaceAll("[^0-9-]", "");
        try {
            return digits.isEmpty() ? -1L : Long.parseLong(digits);
        } catch (NumberFormatException e) {
            return -1L;
        }
    }

    private static LocalDate parseDate(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        String digits = value.replaceAll("[^0-9]", "");
        if (digits.length() < 8) {
            return null;
        }
        try {
            return LocalDate.parse(digits.substring(0, 8), DATE);
        } catch (DateTimeParseException e) {
            return null;
        }
    }

    private static String trim(String value) {
        return value == null ? null : value.trim();
    }
}
