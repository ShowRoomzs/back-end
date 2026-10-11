package showroomz.global.payment.settlement.portone;

import io.portone.sdk.server.common.Bank;
import io.portone.sdk.server.common.Currency;
import io.portone.sdk.server.common.PageInput;
import io.portone.sdk.server.errors.PlatformPartnerNotFoundException;
import io.portone.sdk.server.errors.PlatformTransferAlreadyExistsException;
import io.portone.sdk.server.platform.PlatformAccount;
import io.portone.sdk.server.platform.PlatformClient;
import io.portone.sdk.server.platform.PlatformPartner;
import io.portone.sdk.server.platform.PlatformUserDefinedPropertyValue;
import io.portone.sdk.server.platform.UpdatePlatformPartnerBodyAccount;
import io.portone.sdk.server.platform.account.PlatformAccountHolder;
import io.portone.sdk.server.platform.partnersettlement.GetPlatformPartnerSettlementsResponse;
import io.portone.sdk.server.platform.partnersettlement.PlatformPartnerManualSettlement;
import io.portone.sdk.server.platform.partnersettlement.PlatformPartnerSettlement;
import io.portone.sdk.server.platform.partnersettlement.PlatformPartnerSettlementFilterInput;
import io.portone.sdk.server.platform.payout.GetPlatformPayoutsResponse;
import io.portone.sdk.server.platform.payout.PlatformPayout;
import io.portone.sdk.server.platform.payout.PlatformPayoutFilterInput;
import io.portone.sdk.server.platform.transfer.PlatformManualTransfer;
import io.portone.sdk.server.platform.transfer.PlatformTransfer;
import io.portone.sdk.server.platform.transfer.PlatformUserDefinedPropertyKeyValue;
import lombok.extern.slf4j.Slf4j;
import showroomz.domain.settlement.port.SettlementPayoutGateway;
import showroomz.domain.settlement.service.SettlementPartnerIds;
import showroomz.domain.settlement.type.SettlementPayee;
import showroomz.global.payment.portone.PaymentGatewayException;
import showroomz.global.payment.portone.PaymentGatewayRejectedException;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * 포트원 파트너 정산 지급 어댑터(44_포트원_파트너정산_연동_BE_설계서.md 5절) — 수취자 행 1개 = 수기 정산건 1개. 금액은 우리 산식 그대로
 * ({@code deductWht=false}). 지급 실행은 포트원 콘솔(5-3)이고 결과는 {@link #lookup}(정산건 → 지급 id → 지급 상태)로 닫는다.
 *
 * <p>통신 · 5xx 는 예외로 던진다 — 서비스가 전 행을 REQUESTED 로 두고, 정산건 id 가 결정적이라 결과 조회가 「생겼는가」를 닫는다.
 */
@Slf4j
public class PortOneSettlementPayoutGateway implements SettlementPayoutGateway {

    private static final int PAGE_SIZE = 200;
    private static final int MAX_PAGES = 20;

    private final PlatformClient client;
    private final boolean test;
    private final String idPrefix;
    private final PortOnePlatformCalls calls;

    public PortOneSettlementPayoutGateway(PlatformClient client, boolean test, String idPrefix, long timeoutMillis) {
        this.client = client;
        this.test = test;
        this.idPrefix = idPrefix;
        this.calls = new PortOnePlatformCalls(timeoutMillis);
    }

    // ------------------------------------------------------------------ 지시(5-2)

    @Override
    public PayoutResult distribute(PayoutCommand command) {
        List<LineResult> results = new ArrayList<>();
        String settlementDate = LocalDate.now().toString();
        for (PayoutLine line : command.lines()) {
            if (line.payee() == SettlementPayee.PLATFORM) {
                // 플랫폼 몫은 내부 계정 — 정산건을 올리지 않는다.
                results.add(new LineResult(line.payoutId(), Outcome.PAID, "PLATFORM-" + command.settlementNumber(), null, null));
                continue;
            }
            if (line.payeeRefId() == null) {
                results.add(new LineResult(line.payoutId(), Outcome.FAILED, null, PortOneFailureClassifier.PARTNER_NOT_READY,
                        "수취자 식별자 없음"));
                continue;
            }
            String partnerId = SettlementPartnerIds.partnerId(idPrefix, line.payee(), line.payeeRefId());
            String transferId = SettlementPartnerIds.transferId(idPrefix, command.settlementNumber(), line.payee(), line.attempt());
            try {
                syncAccount(partnerId, line);
            } catch (PaymentGatewayRejectedException e) {
                results.add(new LineResult(line.payoutId(), Outcome.FAILED, null,
                        e.getCause() instanceof PlatformPartnerNotFoundException ? PortOneFailureClassifier.PARTNER_NOT_READY
                                : PortOneFailureClassifier.ACCOUNT_SYNC_FAILED, e.getMessage()));
                continue;
            }
            try {
                String memo = "%s · %s".formatted(command.settlementNumber(), line.payee().getLabel());
                calls.await(client.getTransfer().createPlatformManualTransfer(test, partnerId, memo, line.amount(), null,
                        settlementDate, test, properties(command, line), transferId, Currency.Krw.INSTANCE), "수기 정산건 생성");
                results.add(new LineResult(line.payoutId(), Outcome.REQUESTED, null, null, null, transferId));
            } catch (PaymentGatewayRejectedException e) {
                if (e.getCause() instanceof PlatformTransferAlreadyExistsException) {
                    results.add(new LineResult(line.payoutId(), Outcome.REQUESTED, null, null, null, transferId));
                } else {
                    results.add(new LineResult(line.payoutId(), Outcome.FAILED, null,
                            PortOneFailureClassifier.transferFailCode(e), e.getMessage()));
                }
            }
        }
        return new PayoutResult(results);
    }

    /**
     * 지시 직전 — 파트너의 현재 계좌가 행의 스냅샷 계좌(은행 코드 · 번호)와 다르면 맞춘다(확정 회차는 기존 계좌 · 4-4). 은행 코드를
     * 모르면 번호가 같을 때만 통과시키고, 바꿔야 하는데 코드가 없으면 거절한다(옛 은행으로 새 번호를 등록하는 것보다 실패가 낫다).
     */
    private void syncAccount(String partnerId, PayoutLine line) {
        PlatformPartner partner = calls.await(client.getPartner().getPlatformPartner(partnerId, test), "파트너 조회");
        PlatformAccount current = partner.getAccount();
        Optional<Bank> wanted = PortOneBanks.byCode(line.bankCode());
        boolean sameNumber = current != null && digits(current.getNumber()).equals(digits(line.accountNumber()));
        boolean sameBank = current != null && current.getBank() != null
                && (wanted.isEmpty() || current.getBank().getValue().equals(wanted.get().getValue()));
        if (sameNumber && sameBank) {
            return;
        }
        if (wanted.isEmpty()) {
            throw new PaymentGatewayRejectedException(400, "UNSUPPORTED_BANK",
                    "파트너 계좌 동기화 — 은행 코드를 정할 수 없다 · " + line.bankName() + "(" + line.bankCode() + ")");
        }
        PlatformAccountHolder holder = calls.await(client.getAccount().getPlatformAccountHolder(wanted.get(),
                line.accountNumber(), test, null, null), "계좌 예금주 조회");
        calls.await(client.getPartner().updatePlatformPartner(partnerId, test, null, null,
                new UpdatePlatformPartnerBodyAccount(wanted.get(), Currency.Krw.INSTANCE, line.accountNumber(),
                        line.holder(), holder.getAccountVerificationId()), null, null, null, null, null), "파트너 계좌 갱신");
        log.info("포트원 파트너 계좌 동기화 - partnerId: {}", partnerId);
    }

    private static List<PlatformUserDefinedPropertyKeyValue> properties(PayoutCommand command, PayoutLine line) {
        return List.of(
                new PlatformUserDefinedPropertyKeyValue("settlementId", new PlatformUserDefinedPropertyValue(String.valueOf(command.settlementId()))),
                new PlatformUserDefinedPropertyKeyValue("payoutId", new PlatformUserDefinedPropertyValue(String.valueOf(line.payoutId()))),
                new PlatformUserDefinedPropertyKeyValue("payee", new PlatformUserDefinedPropertyValue(line.payee().name())));
    }

    // ------------------------------------------------------------------ 결과(5-4)

    @Override
    public List<LineResult> lookup(List<PayoutLookup> lookups) {
        List<LineResult> results = new ArrayList<>();
        for (PayoutLookup lookup : lookups) {
            String transferId = lookup.pgTransferId() != null ? lookup.pgTransferId()
                    : SettlementPartnerIds.transferId(idPrefix, lookup.settlementNumber(), lookup.payee(), lookup.attempt());
            try {
                PlatformTransfer transfer = calls.await(client.getTransfer().getPlatformTransfer(transferId, test), "정산건 조회");
                if (!(transfer instanceof PlatformManualTransfer manual)) {
                    log.warn("포트원 정산건 유형 불일치 - transferId: {}", transferId);
                    continue;
                }
                if (manual.getPayoutId() == null) {
                    // 아직 지급 전(콘솔 실행 대기) — 정산건 id 만 적어 둔다(지시 중 통신 실패로 비어 있을 수 있다).
                    results.add(new LineResult(lookup.payoutId(), Outcome.REQUESTED, null, null, null, transferId));
                    continue;
                }
                results.add(payoutResult(lookup, transferId, manual.getPayoutId()));
            } catch (PaymentGatewayRejectedException e) {
                // 정산건이 없다 — 지시가 포트원에 닿지 않았다. 재분배가 새 id 로 다시 올린다(이중 지급 없음).
                log.warn("포트원 정산건 없음 - transferId: {}, {}", transferId, e.getMessage());
                results.add(new LineResult(lookup.payoutId(), Outcome.FAILED, null, PortOneFailureClassifier.TRANSFER_NOT_FOUND,
                        "PG 정산건 미생성 — " + e.getMessage()));
            } catch (PaymentGatewayException e) {
                log.error("포트원 정산건 조회 실패 - transferId: {}", transferId, e);
            }
        }
        return results;
    }

    private LineResult payoutResult(PayoutLookup lookup, String transferId, String payoutId) {
        GetPlatformPayoutsResponse response = calls.await(client.getPayout().getPlatformPayouts(test, test,
                new PageInput(0, 10), new PlatformPayoutFilterInput(null, null, null, null, null, null, List.of(payoutId),
                        null, null, null, null)), "지급 조회");
        PlatformPayout payout = response.getItems().stream().filter(p -> payoutId.equals(p.getId())).findFirst().orElse(null);
        if (payout == null) {
            return new LineResult(lookup.payoutId(), Outcome.REQUESTED, null, null, null, transferId);
        }
        String status = payout.getStatus() == null ? "" : payout.getStatus().getValue();
        return switch (status) {
            case "SUCCEEDED" -> payout.getAmount() == lookup.amount()
                    ? new LineResult(lookup.payoutId(), Outcome.PAID, payoutId, null, null, transferId)
                    // 금액이 다르면 PAID 로 닫지 않는다(설정 — deductWht — 이 틀린 것) · 사람을 부른다.
                    : new LineResult(lookup.payoutId(), Outcome.REQUESTED, payoutId, PortOneFailureClassifier.AMOUNT_MISMATCH,
                    "지급액 %,d ≠ 행 %,d".formatted(payout.getAmount(), lookup.amount()), transferId);
            case "FAILED" -> new LineResult(lookup.payoutId(), Outcome.FAILED, payoutId,
                    PortOneFailureClassifier.payoutFailCode(payout.getFailReason()), payout.getFailReason(), transferId);
            case "CANCELLED", "STOPPED" -> new LineResult(lookup.payoutId(), Outcome.FAILED, payoutId,
                    PortOneFailureClassifier.CANCELLED_AT_PG, "PG 지급 " + status, transferId);
            case "SCHEDULED", "PREPARED", "PROCESSING", "CONFIRMED" ->
                    new LineResult(lookup.payoutId(), Outcome.REQUESTED, payoutId, null, null, transferId);
            default -> {
                log.warn("포트원 지급 상태 미매핑 - payoutId: {}, status: {}", payoutId, status);
                yield new LineResult(lookup.payoutId(), Outcome.REQUESTED, payoutId, null, null, transferId);
            }
        };
    }

    // ------------------------------------------------------------------ 대조(5-3)

    @Override
    public Optional<Reconciliation> reconcile(LocalDate settlementDate) {
        int count = 0;
        long amount = 0;
        for (int page = 0; page < MAX_PAGES; page++) {
            GetPlatformPartnerSettlementsResponse response = calls.await(client.getPartnerSettlement()
                    .getPlatformPartnerSettlements(test, new PageInput(page, PAGE_SIZE),
                            new PlatformPartnerSettlementFilterInput(List.of(settlementDate.toString()), null, null, null,
                                    null, null, null, null), test), "파트너 정산 조회");
            for (PlatformPartnerSettlement item : response.getItems()) {
                if (item instanceof PlatformPartnerManualSettlement manual) {
                    count++;
                    amount += manual.getAmount();
                }
            }
            if (response.getItems().size() < PAGE_SIZE) {
                break;
            }
        }
        return Optional.of(new Reconciliation(count, amount));
    }

    private static String digits(String value) {
        return value == null ? "" : value.replaceAll("[^0-9]", "");
    }
}
