package showroomz.global.payment.settlement.toss;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import showroomz.domain.settlement.port.SettlementPayoutGateway;
import showroomz.domain.settlement.service.SettlementPartnerIds;
import showroomz.domain.settlement.type.SettlementPayee;
import showroomz.global.config.properties.TossPayoutsProperties;
import showroomz.global.payment.portone.PaymentGatewayException;
import showroomz.global.payment.portone.PaymentGatewayRejectedException;
import showroomz.global.payment.settlement.toss.TossPayoutsDtos.Account;
import showroomz.global.payment.settlement.toss.TossPayoutsDtos.Money;
import showroomz.global.payment.settlement.toss.TossPayoutsDtos.Payout;
import showroomz.global.payment.settlement.toss.TossPayoutsDtos.PayoutRequest;
import showroomz.global.payment.settlement.toss.TossPayoutsDtos.Seller;
import showroomz.global.utils.BusinessCalendar;

import java.io.IOException;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * 토스 지급대행 어댑터(44 토스 설계서 5절) — 수취자 행 1개 = 지급 요청 1건(묶음은 하나라도 실패하면 전체 실패라 쓰지 않는다). 금액은 우리
 * 산식 그대로(0-2). 지시 시각이 EXPRESS 창 안이면 당일, 아니면 다음 영업일 SCHEDULED(0-4). 결과는 웹훅({@link #fromWebhook}) ·
 * 폴링({@link #lookup}) 이 같은 매핑(5-4)으로 닫는다.
 *
 * <p>통신 · 5xx · 「처리 중」 코드는 예외로 던진다 — 서비스가 전 행을 REQUESTED 로 두고, 멱등 키가 같아 재시도해도 중복이 없다(0-6).
 */
@Slf4j
public class TossSettlementPayoutGateway implements SettlementPayoutGateway {

    static final String EXPRESS = "EXPRESS";
    static final String SCHEDULED = "SCHEDULED";

    private final TossPayoutsClient client;
    private final String refPrefix;
    private final TossPayoutsProperties properties;
    private final BusinessCalendar businessCalendar;
    private final ObjectMapper objectMapper;

    public TossSettlementPayoutGateway(TossPayoutsClient client, boolean test, TossPayoutsProperties properties,
                                       BusinessCalendar businessCalendar, ObjectMapper objectMapper) {
        this.client = client;

        this.refPrefix = test ? properties.getPartnerRefPrefixTest() : "";
        this.properties = properties;
        this.businessCalendar = businessCalendar;
        this.objectMapper = objectMapper;
    }

    // ------------------------------------------------------------------ 잔액(5-1 ①)

    @Override
    public Optional<Long> availableBalance() {
        return Optional.of(client.balance().available());
    }

    // ------------------------------------------------------------------ 지시(5-2)

    @Override
    public PayoutResult distribute(PayoutCommand command) {
        List<LineResult> results = new ArrayList<>();
        Schedule schedule = scheduleFor(command.requestedAt() == null ? LocalDateTime.now() : command.requestedAt());
        for (PayoutLine line : command.lines()) {
            if (line.payee() == SettlementPayee.PLATFORM) {
                // 플랫폼 몫은 내부 계정 — 요청하지 않는다.
                results.add(new LineResult(line.payoutId(), Outcome.PAID, "PLATFORM-" + command.settlementNumber(), null, null));
                continue;
            }
            if (line.partnerExternalId() == null || line.partnerExternalId().isBlank()) {
                results.add(new LineResult(line.payoutId(), Outcome.FAILED, null, TossPayoutFailureClassifier.PARTNER_NOT_READY,
                        "PG 셀러 미연결 — 확정 때 등록되지 않았다"));
                continue;
            }
            String payoutRef = SettlementPartnerIds.payoutRef(refPrefix, command.settlementNumber(), line.payee(), line.attempt());
            try {
                syncAccount(line);
            } catch (PaymentGatewayRejectedException e) {
                String code = TossPayoutFailureClassifier.NOT_FOUND.equals(e.getType())
                        ? TossPayoutFailureClassifier.PARTNER_NOT_READY : TossPayoutFailureClassifier.ACCOUNT_SYNC_FAILED;
                results.add(new LineResult(line.payoutId(), Outcome.FAILED, null, code, e.getMessage()));
                continue;
            }
            results.add(request(command, line, payoutRef, schedule));
        }
        return new PayoutResult(results);
    }

    private LineResult request(PayoutCommand command, PayoutLine line, String payoutRef, Schedule schedule) {
        Map<String, String> metadata = new LinkedHashMap<>();
        metadata.put("settlementId", String.valueOf(command.settlementId()));
        metadata.put("payoutId", String.valueOf(line.payoutId()));
        metadata.put("payee", line.payee().name());
        metadata.put("settlementNumber", command.settlementNumber());
        PayoutRequest request = new PayoutRequest(payoutRef, line.partnerExternalId(), schedule.type(),
                schedule.type().equals(SCHEDULED) ? schedule.payoutDate().toString() : null, Money.krw(line.amount()),
                properties.getTransactionDescription(), metadata);
        try {
            Payout payout = client.requestPayout(request, SettlementPartnerIds.idempotencyKey(payoutRef));
            return fromPayout(line.payoutId(), line.amount(), payoutRef, payout, true);
        } catch (PaymentGatewayRejectedException e) {
            String tossCode = e.getType();
            if (TossPayoutFailureClassifier.DUPLICATE_PAYOUT_REQUEST.equals(tossCode)) {
                // 멱등 키 15일 만료 뒤 재시도 — 토스에 이미 있다. id 는 결과 조회(목록)가 복구한다(5-4 ②).
                return new LineResult(line.payoutId(), Outcome.REQUESTED, null, null, null, payoutRef, schedule.type(),
                        schedule.payoutDate());
            }
            if (TossPayoutFailureClassifier.EXCEEDED_PAYOUT_BALANCE_AMOUNT.equals(tossCode)) {
                return new LineResult(line.payoutId(), Outcome.DEFERRED, null, TossPayoutFailureClassifier.INSUFFICIENT_BALANCE,
                        e.getMessage(), payoutRef, null, null);
            }
            if (TossPayoutFailureClassifier.FORBIDDEN_SELLER_PAYOUT.equals(tossCode)) {
                // 본인인증 · KYC 필요 — 셀러 상태로 가른다. 서비스가 FAILED 대신 BLOCKED 로 되돌린다(5-2 ④).
                String raw = sellerStatusQuietly(line.partnerExternalId());
                return new LineResult(line.payoutId(), Outcome.FAILED, null, TossPayoutFailureClassifier.blockCodeOf(raw),
                        e.getMessage(), payoutRef, null, null);
            }
            String code = TossPayoutFailureClassifier.payoutRequestFailCode(tossCode);
            if (TossPayoutFailureClassifier.REJECTED_BY_PG.equals(code)) {
                log.error("토스 지급 요청 거절(우리 요청 오류 가능) - ref: {}, {}", payoutRef, e.getMessage());
            }
            return new LineResult(line.payoutId(), Outcome.FAILED, null, code, e.getMessage(), payoutRef, null, null);
        }
    }

    /**
     * 지시 직전 — 셀러의 현재 계좌가 행의 스냅샷 계좌(은행 코드 · 번호)와 다르면 맞춘다(확정 회차는 기존 계좌 · 4-4). 미완료 지급이 있으면
     * 토스가 거절({@code FORBIDDEN_SELLER_MODIFY})해 다음 영업일로 간다.
     */
    private void syncAccount(PayoutLine line) {
        Seller seller = client.getSeller(line.partnerExternalId());
        Account current = seller.account();
        boolean sameNumber = current != null && digits(current.accountNumber()).equals(digits(line.accountNumber()));
        boolean sameBank = current != null && current.bankCode() != null
                && (line.bankCode() == null || current.bankCode().trim().equals(line.bankCode().trim()));
        if (sameNumber && sameBank) {
            return;
        }
        if (line.bankCode() == null) {
            throw new PaymentGatewayRejectedException(400, TossPayoutFailureClassifier.UNSUPPORTED_BANK,
                    "셀러 계좌 동기화 — 은행 코드를 정할 수 없다 · " + line.bankName());
        }
        client.updateSeller(line.partnerExternalId(), Map.of("account",
                new Account(line.bankCode().trim(), digits(line.accountNumber()), line.holder())));
        log.info("토스 셀러 계좌 동기화 - sellerId: {}", line.partnerExternalId());
    }

    /** 지시 시각 → 유형(0-4). 영업일 [start, end) 안이면 EXPRESS, 아니면 다음 영업일 SCHEDULED. */
    Schedule scheduleFor(LocalDateTime requestedAt) {
        LocalDate day = requestedAt.toLocalDate();
        LocalTime time = requestedAt.toLocalTime();
        boolean inWindow = businessCalendar.isBusinessDay(day) && !time.isBefore(properties.getExpressWindowStart())
                && time.isBefore(properties.getExpressWindowEnd());
        return inWindow ? new Schedule(EXPRESS, day) : new Schedule(SCHEDULED, businessCalendar.addBusinessDays(day, 1));
    }

    record Schedule(String type, LocalDate payoutDate) {
    }

    // ------------------------------------------------------------------ 결과(5-4)

    @Override
    public List<LineResult> lookup(List<PayoutLookup> lookups) {
        List<LineResult> results = new ArrayList<>();
        for (PayoutLookup lookup : lookups) {
            if (lookup.payee() == SettlementPayee.PLATFORM) {
                // 내부 계정 — PG 에 간 적이 없다(지시 중 통신 실패로 REQUESTED 가 남은 경우). 바로 닫는다.
                results.add(new LineResult(lookup.payoutId(), Outcome.PAID, "PLATFORM-" + lookup.settlementNumber(), null, null));
                continue;
            }
            String payoutRef = lookup.pgTransferId() != null ? lookup.pgTransferId()
                    : SettlementPartnerIds.payoutRef(refPrefix, lookup.settlementNumber(), lookup.payee(), lookup.attempt());
            try {
                if (lookup.pgPayoutId() != null) {
                    Payout payout = client.getPayout(lookup.pgPayoutId());
                    results.add(fromPayout(lookup.payoutId(), lookup.amount(), payoutRef, payout, false));
                    continue;
                }
                // 결과 모름(지시 중 통신 실패) — 지급일 범위 목록에서 참조 id 로 찾는다.
                LocalDate from = lookup.requestedAt() == null ? LocalDate.now().minusDays(7) : lookup.requestedAt().toLocalDate();
                LocalDate to = businessCalendar.addBusinessDays(from, 1);
                Payout found = client.listPayouts(from, to).stream().filter(p -> payoutRef.equals(p.refPayoutId()))
                        .findFirst().orElse(null);
                if (found != null) {
                    results.add(fromPayout(lookup.payoutId(), lookup.amount(), payoutRef, found, false));
                } else if (lookup.requestedAt() != null && !businessCalendar.addBusinessDays(lookup.requestedAt().toLocalDate(),
                        properties.getNotFoundGraceBusinessDays()).isAfter(LocalDate.now())) {
                    // 유예가 지났는데도 없다 — 지시가 토스에 닿지 않았다. 재분배가 새 ref 로 올린다(9-5 절차 뒤).
                    results.add(new LineResult(lookup.payoutId(), Outcome.FAILED, null, TossPayoutFailureClassifier.TRANSFER_NOT_FOUND,
                            "PG 지급 미생성 — " + payoutRef, payoutRef, null, null));
                }
            } catch (PaymentGatewayRejectedException e) {
                if (TossPayoutFailureClassifier.NOT_FOUND.equals(e.getType())) {
                    results.add(new LineResult(lookup.payoutId(), Outcome.FAILED, lookup.pgPayoutId(),
                            TossPayoutFailureClassifier.TRANSFER_NOT_FOUND, "PG 지급 없음 — " + e.getMessage(), payoutRef, null, null));
                } else {
                    log.warn("토스 지급 조회 거절 - ref: {}, {}", payoutRef, e.getMessage());
                }
            } catch (PaymentGatewayException e) {
                log.error("토스 지급 조회 실패 - ref: {}", payoutRef, e);
            }
        }
        return results;
    }

    @Override
    public Optional<LineResult> fromWebhook(String payoutJson, PayoutLookup lookup) {
        try {
            Payout payout = objectMapper.readValue(payoutJson, Payout.class);
            String payoutRef = lookup.pgTransferId() != null ? lookup.pgTransferId() : payout.refPayoutId();
            return Optional.of(fromPayout(lookup.payoutId(), lookup.amount(), payoutRef, payout, false));
        } catch (IOException e) {
            log.warn("토스 웹훅 지급 객체 해석 실패 - payoutId: {}, {}", lookup.payoutId(), e.getMessage());
            return Optional.empty();
        }
    }

    /** 토스 {@code Payout.status} → 우리 결과(5-4 표). */
    LineResult fromPayout(Long payoutId, long amount, String payoutRef, Payout payout, boolean justRequested) {
        String status = payout.status() == null ? "" : payout.status();
        LocalDate payoutDate = payout.payoutDate() == null ? null : LocalDate.parse(payout.payoutDate());
        String type = payout.scheduleType();
        String id = payout.id();
        return switch (status) {
            case "COMPLETED" -> {
                if (payout.amountValue() >= 0 && payout.amountValue() != amount) {
                    log.warn("토스 지급액 ≠ 행 금액 - id: {}, 지급 {}, 행 {}", id, payout.amountValue(), amount);
                }
                yield new LineResult(payoutId, Outcome.PAID, id, null, null, payoutRef, type, payoutDate);
            }
            case "FAILED", "REJECTED" -> {
                String code = payout.error() == null ? null : payout.error().code();
                String message = payout.error() == null ? null : payout.error().message();
                yield new LineResult(payoutId, Outcome.FAILED, id, TossPayoutFailureClassifier.payoutResultFailCode(code, message),
                        (code == null ? status : code) + (message == null ? "" : ": " + message), payoutRef, type, payoutDate);
            }
            case "CANCELED" -> new LineResult(payoutId, Outcome.FAILED, id, TossPayoutFailureClassifier.CANCELLED_AT_PG,
                    "PG 지급 취소", payoutRef, type, payoutDate);
            case "DELETED" -> new LineResult(payoutId, Outcome.FAILED, id, TossPayoutFailureClassifier.TRANSFER_NOT_FOUND,
                    "PG 지급요청서 삭제", payoutRef, type, payoutDate);
            case "REQUESTED", "IN_PROGRESS" -> new LineResult(payoutId, Outcome.REQUESTED, id, null, null, payoutRef, type, payoutDate);
            default -> {
                if (!justRequested) {
                    log.warn("토스 지급 상태 미매핑 - id: {}, status: {}", id, status);
                }
                yield new LineResult(payoutId, Outcome.REQUESTED, id, null, null, payoutRef, type, payoutDate);
            }
        };
    }

    // ------------------------------------------------------------------ 대사(5-3)

    @Override
    public Optional<Reconciliation> reconcile(LocalDate payoutDate) {
        List<Payout> payouts = client.listPayouts(payoutDate, payoutDate);
        long amount = payouts.stream().mapToLong(Payout::amountValue).filter(v -> v >= 0).sum();
        return Optional.of(new Reconciliation(payouts.size(), amount));
    }

    private String sellerStatusQuietly(String sellerId) {
        try {
            return client.getSeller(sellerId).status();
        } catch (RuntimeException e) {
            return null;
        }
    }

    private static String digits(String value) {
        return value == null ? "" : value.replaceAll("[^0-9]", "");
    }
}
