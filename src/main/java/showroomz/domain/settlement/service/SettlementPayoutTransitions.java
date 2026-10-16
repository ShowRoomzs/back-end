package showroomz.domain.settlement.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import showroomz.domain.bank.entity.Bank;
import showroomz.domain.bank.repository.BankRepository;
import showroomz.domain.groupbuy.entity.GroupBuy;
import showroomz.domain.groupbuy.repository.GroupBuyRepository;
import showroomz.domain.groupbuy.service.GroupBuyCommandService;
import showroomz.domain.groupbuy.type.GroupBuyStatus;
import showroomz.domain.member.creator.entity.Creator;
import showroomz.domain.member.seller.entity.Seller;
import showroomz.domain.settlement.entity.Settlement;
import showroomz.domain.settlement.entity.SettlementPayout;
import showroomz.domain.settlement.port.SettlementPartnerGateway;
import showroomz.domain.settlement.port.SettlementPayoutGateway.LineResult;
import showroomz.domain.settlement.port.SettlementPayoutGateway.Outcome;
import showroomz.domain.settlement.port.SettlementPayoutGateway.PayoutCommand;
import showroomz.domain.settlement.port.SettlementPayoutGateway.PayoutLine;
import showroomz.domain.settlement.port.SettlementPayoutGateway.PayoutResult;
import showroomz.domain.settlement.repository.SettlementPayoutRepository;
import showroomz.domain.settlement.repository.SettlementRepository;
import showroomz.domain.settlement.type.PayoutBlockReason;
import showroomz.domain.settlement.type.PayoutStatus;
import showroomz.domain.settlement.type.SettlementActorType;
import showroomz.domain.settlement.type.SettlementEventType;
import showroomz.domain.settlement.type.SettlementPayee;
import showroomz.domain.settlement.type.SettlementStatus;
import showroomz.global.error.exception.BusinessException;
import showroomz.global.config.properties.SettlementProperties;
import showroomz.global.error.exception.ErrorCode;
import showroomz.global.utils.BusinessCalendar;
import showroomz.global.utils.PersonalDataCipher;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 지급의 짧은 트랜잭션들(44 어드민 설계서 3-3) — PG 호출은 여기 없다. {@code SettlementPayoutService}가 트랜잭션 밖에서
 * 게이트웨이를 부르고 결과를 가져온다(환불 집행 {@code RefundTransitions}와 같은 구조).
 *
 * <p>행마다 조건부 UPDATE(SCHEDULED → REQUESTED → PAID / FAILED) · 정산 상태는 행 전체에서 <b>파생해 저장</b>한다(0-4): 하나라도
 * FAILED 면 PAYOUT_FAILED, 전부 PAID(또는 NOT_APPLICABLE)면 PAID · 공구 SETTLED.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class SettlementPayoutTransitions {

    public static final String FAIL_ACCOUNT_MISSING = "ACCOUNT_MISSING";
    private static final String PLATFORM_ACCOUNT = "SHOWROOMZ";

    private final SettlementRepository settlementRepository;
    private final SettlementPayoutRepository payoutRepository;
    private final SettlementHistoryRecorder historyRecorder;
    private final SettlementTaxDocumentHook taxDocumentHook;
    private final SettlementNotifier notifier;
    private final PersonalDataCipher cipher;
    private final GroupBuyRepository groupBuyRepository;
    private final GroupBuyCommandService groupBuyCommandService;
    private final PayoutBlockPolicy blockPolicy;
    private final BankRepository bankRepository;
    private final SettlementPartnerGateway partnerGateway;
    private final SettlementProperties properties;
    private final BusinessCalendar businessCalendar;
    private final showroomz.domain.settlement.port.BrandPayoutHoldReader brandPayoutHoldReader;
    private final showroomz.domain.settlement.port.CreatorPayoutHoldReader creatorPayoutHoldReader;

    /** 지급 계좌 — 스냅샷의 원천(회원 정보 · 브랜드는 확정 시점 · 그 밖은 지시 시점). 플랫폼은 내부 계정이다. */
    public record Account(String bankName, String accountNumber, String holder) {
        public boolean isMissing() {
            return bankName == null || bankName.isBlank() || accountNumber == null || accountNumber.isBlank();
        }
    }

    /**
     * 지시 선점 — 예정일이 된 SCHEDULED 행에 계좌를 스냅샷하고 REQUESTED 로 올린다. 계좌가 없으면 게이트웨이를 부르지 않고
     * {@code FAILED(ACCOUNT_MISSING)} — 운영자 재분배로 간다(13절 A-5). 0원 행은 지시하지 않는다(가드).
     *
     * @return 게이트웨이에 보낼 지시 — 지급할 정산이 아니면 null
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public PayoutCommand claim(Long settlementId, LocalDate today, LocalDateTime now) {
        Settlement settlement = settlementRepository.findForUpdate(settlementId).orElse(null);
        if (settlement == null || (settlement.getStatus() != SettlementStatus.PAYOUT_SCHEDULED
                && settlement.getStatus() != SettlementStatus.PAYOUT_FAILED)) {
            return null;
        }
        // 회원 정보는 행 UPDATE(영속성 컨텍스트 비움) 전에 읽는다.
        Seller seller = settlement.getMarket().getSeller();
        Creator creatorEntity = settlement.getCreator();
        Account brand = accountOf(seller);
        Account creator = accountOf(creatorEntity);
        String settlementNumber = settlement.getSettlementNumber();
        Long marketId = settlement.getMarketId();
        Long creatorId = settlement.getCreatorId();
        // PG 셀러 연결(토스 설계서 5-2) — 이 환경의 연결만. 상태가 한도 있는 승인(PARTIALLY_APPROVED)이면 주당 한도를 먼저 센다(5-2 ①).
        String brandSellerId = seller == null || !partnerGateway.env().equals(seller.getPgPartnerEnv()) ? null : seller.getPgPartnerId();
        String creatorSellerId = creatorEntity == null || !partnerGateway.env().equals(creatorEntity.getPgPartnerEnv()) ? null
                : creatorEntity.getPgPartnerId();
        String brandRawStatus = seller == null ? null : seller.getPgPartnerStatus();
        String creatorRawStatus = creatorEntity == null ? null : creatorEntity.getPgPartnerStatus();
        boolean brandHeld = brandPayoutHoldReader.isHeld(marketId);
        boolean creatorHeld = creatorId != null && creatorPayoutHoldReader.isHeld(creatorId);

        List<PayoutLine> lines = new ArrayList<>();
        for (SettlementPayout payout : payoutRepository.findBySettlementId(settlementId)) {
            if (payout.getStatus() != PayoutStatus.SCHEDULED || payout.getDueDate() == null
                    || payout.getDueDate().isAfter(today)) {
                continue;
            }
            if (payout.getAmount() <= 0) {
                payoutRepository.updateStatusWhere(payout.getId(), EnumSet.of(PayoutStatus.SCHEDULED),
                        PayoutStatus.NOT_APPLICABLE, payout.getDueDate());
                continue;
            }
            // 계정 정지 · 지급 정지(52 설계서 6-3) — 정지 직후 배치 · 확정과의 경합 틈새를 이 재판정이 닫는다. 해제가 다시 예정으로 올린다.
            if (payout.getPayee() == SettlementPayee.BRAND && brandHeld) {
                if (payoutRepository.blockWithReason(payout.getId(), PayoutBlockReason.BRAND_SUSPENDED.name(),
                        PayoutBlockReason.BRAND_SUSPENDED.getLabel()) == 1) {
                    historyRecorder.recordBySystem(settlementId, SettlementEventType.PAYOUT_BLOCKED,
                            "브랜드 몫 · " + PayoutBlockReason.BRAND_SUSPENDED.getLabel(), now);
                }
                continue;
            }
            // 인플루언서 정지 · 정산 정보 불일치(53 설계서 4-3) — 세금계산서 확인 · 주민번호 등록으로 예정에 올라온 행도 여기서 막는다.
            if (payout.getPayee() == SettlementPayee.CREATOR && creatorHeld) {
                if (payoutRepository.blockWithReason(payout.getId(), PayoutBlockReason.CREATOR_SUSPENDED.name(),
                        PayoutBlockReason.CREATOR_SUSPENDED.getLabel()) == 1) {
                    historyRecorder.recordBySystem(settlementId, SettlementEventType.PAYOUT_BLOCKED,
                            "인플루언서 몫 · " + PayoutBlockReason.CREATOR_SUSPENDED.getLabel(), now);
                }
                continue;
            }
            // 재분배(M3)가 고른 계좌(현재 회원 정보 · 지난 지시 그대로)가 스냅샷으로 남아 있으면 그것으로 지시한다.
            Account account = payout.hasAccountSnapshot()
                    ? new Account(payout.getBankName(), cipher.decrypt(payout.getAccountNumberEnc()),
                    payout.getAccountHolder())
                    : switch (payout.getPayee()) {
                        case BRAND -> brand;
                        case CREATOR -> creator;
                        case PLATFORM -> new Account(PLATFORM_ACCOUNT, null, PLATFORM_ACCOUNT);
                    };
            if (payout.getPayee() != SettlementPayee.PLATFORM && account.isMissing()) {
                if (payoutRepository.markFailed(payout.getId(), null, FAIL_ACCOUNT_MISSING, "등록 계좌 없음", now) == 1) {
                    historyRecorder.recordBySystem(settlementId, SettlementEventType.PAYOUT_FAILED,
                            payout.getPayee().getLabel() + " · 등록 계좌 없음", now);
                    notifier.payoutFailed(settlementId, payout.getPayee());
                }
                continue;
            }
            // 주당 한도(토스 설계서 5-2 ①) — 넘기면 PG 가 요청을 취소하고 셀러를 KYC_REQUIRED 로 넘겨 다른 회차까지 막힌다. 먼저 보류한다.
            String rawStatus = payout.getPayee() == SettlementPayee.BRAND ? brandRawStatus
                    : payout.getPayee() == SettlementPayee.CREATOR ? creatorRawStatus : null;
            if (rawStatus != null && partnerGateway.isLimited(rawStatus)
                    && exceedsWeeklyLimit(payout.getPayee(), marketId, creatorId, payout.getAmount(), now)) {
                String code = PayoutBlockReason.KYC_REQUIRED.name();
                if (payoutRepository.blockWithReason(payout.getId(), code, "주당 한도 초과 예상 — KYC 완료 후 지급") == 1) {
                    historyRecorder.recordBySystem(settlementId, SettlementEventType.PAYOUT_BLOCKED,
                            payout.getPayee().getLabel() + " · " + PayoutBlockReason.KYC_REQUIRED.getLabel() + " · 주당 한도 초과 예상", now);
                    notifier.payoutCheckRequired(settlementId, payout.getPayee(), code);
                    notifier.partnerVerificationRequired(settlementId, payout.getPayee(), code);
                }
                continue;
            }
            if (payoutRepository.markRequested(payout.getId(), account.bankName(), cipher.encrypt(account.accountNumber()),
                    account.holder(), now) == 1) {
                Long payeeRefId = switch (payout.getPayee()) {
                    case BRAND -> marketId;
                    case CREATOR -> creatorId;
                    case PLATFORM -> null;
                };
                String bankCode = account.bankName() == null ? null
                        : bankRepository.findByName(account.bankName().trim()).map(Bank::getCode).orElse(null);
                String partnerExternalId = switch (payout.getPayee()) {
                    case BRAND -> brandSellerId;
                    case CREATOR -> creatorSellerId;
                    case PLATFORM -> null;
                };
                lines.add(new PayoutLine(payout.getId(), payout.getPayee(), payout.getAmount(), account.bankName(),
                        account.accountNumber(), account.holder(), payeeRefId, payout.getAttempt(), bankCode, partnerExternalId));
                historyRecorder.recordBySystem(settlementId, SettlementEventType.PAYOUT_REQUESTED,
                        "%s · %,d원 지급 지시".formatted(payout.getPayee().getLabel(), payout.getAmount()), now);
            }
        }
        return new PayoutCommand(settlementId, settlementNumber, lines, now);
    }

    /** 최근 7일(지시 시각 기준) REQUESTED · PAID 합 + 이 행 ≥ 한도. */
    private boolean exceedsWeeklyLimit(SettlementPayee payee, Long marketId, Long creatorId, long amount, LocalDateTime now) {
        LocalDateTime since = now.minusDays(7);
        long recent = switch (payee) {
            case BRAND -> payoutRepository.sumRecentForMarket(marketId, since);
            case CREATOR -> payoutRepository.sumRecentForCreator(creatorId, since);
            case PLATFORM -> 0L;
        };
        return recent + amount >= properties.getPayout().getWeeklyLimitAmount();
    }

    /** 게이트웨이 결과 반영 — 행마다 조건부 UPDATE · 이력(PG) · 정산 상태 재파생. */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void applyResults(Long settlementId, PayoutCommand command, PayoutResult result, LocalDateTime now) {
        Map<Long, PayoutLine> sent = command.lines().stream()
                .collect(Collectors.toMap(PayoutLine::payoutId, Function.identity()));
        applyLines(settlementId, sent, result.lines(), now, false);
    }

    /**
     * 결과 조회 · 웹훅(토스 설계서 5-4) 반영 — REQUESTED 행을 PG 가 알려 준 대로 닫는다. 돌려주지 않은 행은 그대로다.
     * REQUESTED 로 남기되 사유(failCode — 지급액 불일치 등)가 붙은 것은 사람을 부른다(이력 1회 · 사유가 바뀔 때만).
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void applyLookupResults(Long settlementId, List<LineResult> results, LocalDateTime now) {
        Map<Long, SettlementPayout> rows = payoutRepository.findBySettlementId(settlementId).stream()
                .filter(p -> p.getStatus() == PayoutStatus.REQUESTED)
                .collect(Collectors.toMap(SettlementPayout::getId, Function.identity()));
        Map<Long, PayoutLine> lines = new java.util.HashMap<>();
        Map<Long, String> previousCodes = new java.util.HashMap<>();
        for (SettlementPayout row : rows.values()) {
            lines.put(row.getId(), new PayoutLine(row.getId(), row.getPayee(), row.getAmount(), null, null, null, null,
                    row.getAttempt()));
            previousCodes.put(row.getId(), row.getFailCode());
        }
        for (LineResult line : results) {
            PayoutLine request = lines.get(line.payoutId());
            if (request == null || line.outcome() != Outcome.REQUESTED || line.failCode() == null
                    || line.failCode().equals(previousCodes.get(line.payoutId()))) {
                continue;
            }
            historyRecorder.record(settlementId, SettlementEventType.PAYOUT_CHECK_REQUIRED, SettlementActorType.PG, null,
                    "%s · %s · %s".formatted(request.payee().getLabel(), line.failCode(),
                            line.failReason() == null ? "" : line.failReason()), now);
            notifier.payoutCheckRequired(settlementId, request.payee(), line.failCode());
        }
        applyLines(settlementId, lines, results, now, true);
    }

    private void applyLines(Long settlementId, Map<Long, PayoutLine> sent, List<LineResult> results, LocalDateTime now,
                            boolean fromLookup) {
        boolean creatorPaid = false;
        for (LineResult line : results) {
            PayoutLine request = sent.get(line.payoutId());
            if (request == null) {
                continue;
            }
            switch (line.outcome()) {
                case PAID -> {
                    int updated = fromLookup
                            ? payoutRepository.markPaidByPg(line.payoutId(), line.pgReference(), now)
                            : payoutRepository.markPaid(line.payoutId(), line.pgReference(), now);
                    if (updated == 1) {
                        historyRecorder.record(settlementId, SettlementEventType.PAYOUT_PAID, SettlementActorType.PG,
                                null, "%s · %,d원 · %s".formatted(request.payee().getLabel(), request.amount(),
                                        line.pgReference()), now);
                        notifier.settlementPaid(settlementId, request.payee());
                        creatorPaid |= request.payee() == SettlementPayee.CREATOR;
                    }
                }
                case FAILED -> {
                    if (!fromLookup && line.failCode() != null && PayoutBlockReason.PAYEE_ACTION_CODES.contains(line.failCode())) {
                        // 본인인증 · KYC 필요(토스 설계서 5-2 ④) — 분배 실패가 아니라 보류. 수취자가 풀면 다시 예정으로. attempt 불변.
                        if (payoutRepository.blockRequested(line.payoutId(), line.failCode(), line.failReason()) == 1) {
                            historyRecorder.record(settlementId, SettlementEventType.PAYOUT_BLOCKED, SettlementActorType.PG, null,
                                    "%s · %s".formatted(request.payee().getLabel(), PayoutBlockReason.fromCode(line.failCode())
                                            .map(PayoutBlockReason::getLabel).orElse(line.failCode())), now);
                            notifier.partnerVerificationRequired(settlementId, request.payee(), line.failCode());
                        }
                        continue;
                    }
                    if (payoutRepository.markFailed(line.payoutId(), line.pgReference(), line.failCode(),
                            line.failReason(), now) == 1) {
                        historyRecorder.record(settlementId, SettlementEventType.PAYOUT_FAILED, SettlementActorType.PG,
                                null, "%s · %s".formatted(request.payee().getLabel(),
                                        line.failReason() == null ? "분배 실패" : line.failReason()), now);
                        notifier.payoutFailed(settlementId, request.payee());
                    }
                }
                case REQUESTED -> {
                    if (line.pgReference() != null) {
                        // 지급 요청이 받아들여졌다(토스 설계서 5-2) — 참조 id · PG 지급 id(= 참조번호) · 유형 · 지급일.
                        payoutRepository.recordRequest(line.payoutId(), line.pgTransferId(), line.pgReference(),
                                line.scheduleType(), line.payoutDate());
                    } else if (line.pgTransferId() != null || line.failCode() != null) {
                        payoutRepository.recordTransfer(line.payoutId(), line.pgTransferId(), line.failCode(),
                                line.failReason());
                    }
                }
                case DEFERRED -> {
                    // 잔액 부족(토스 설계서 5-1 ①) — 지시를 되돌려 다음 영업일로. attempt 를 소모하지 않는다.
                    LocalDate next = businessCalendar.addBusinessDays(now.toLocalDate(), 1);
                    if (payoutRepository.deferRequested(line.payoutId(), next) == 1) {
                        historyRecorder.recordBySystem(settlementId, SettlementEventType.PAYOUT_DEFERRED,
                                "%s · 잔액 부족 · %s 로 연기".formatted(request.payee().getLabel(), next), now);
                        notifier.payoutCheckRequired(settlementId, request.payee(),
                                line.failCode() == null ? "INSUFFICIENT_BALANCE" : line.failCode());
                    }
                }
            }
        }
        if (creatorPaid) {
            taxDocumentHook.onCreatorPaid(settlementId, now);
        }
        derive(settlementId, now);
    }

    /**
     * 셀러 사유 보류 해제(토스 설계서 4-3) — 셀러가 지급 가능이 됐을 때. 인플루언서 행에 다른 보류 사유(주민등록번호 · 세금계산서)가
     * 남아 있으면 사유만 지우고 BLOCKED 로 둔다(기존 재판정이 푼다).
     *
     * @return 지급 예정으로 옮겼으면 true
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public boolean releasePartnerBlocked(Long settlementId, Long payoutId, SettlementPayee payee, LocalDate today) {
        Settlement settlement = settlementRepository.findForUpdate(settlementId).orElse(null);
        if (settlement == null) {
            return false;
        }
        if (payee == SettlementPayee.CREATOR && blockPolicy.isBlocked(settlement)) {
            payoutRepository.releaseBlocked(payoutId, PayoutStatus.BLOCKED, null);
            return false;
        }
        LocalDate due = settlement.getPayoutDueDate() == null || settlement.getPayoutDueDate().isBefore(today)
                ? today : settlement.getPayoutDueDate();
        return payoutRepository.releaseBlocked(payoutId, PayoutStatus.SCHEDULED, due) == 1;
    }

    /** 파트너 재등록이 다른 사유로 또 실패했다 — 보류 사유만 바꾼다. */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void updateBlockReason(Long payoutId, String failCode, String failReason) {
        payoutRepository.updateBlockReason(payoutId, failCode, failReason);
    }

    /** PG 처리 지연(토스 설계서 5-4) — 지시 뒤 N영업일이 지나도 REQUESTED 가 닫히지 않는다. 이력 · 운영자 알림. */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void markStale(Long settlementId, SettlementPayee payee, LocalDateTime now) {
        historyRecorder.recordBySystem(settlementId, SettlementEventType.PAYOUT_CHECK_REQUIRED,
                payee.getLabel() + " · PG 처리 지연 — 지급 결과가 아직 없습니다", now);
        notifier.payoutStale(settlementId);
    }

    /**
     * 잔액 부족 연기(토스 설계서 5-1 ①) — 오늘 예정 행(플랫폼 제외)을 다음 영업일로 미룬다. 지시 전이라 attempt · 계좌 스냅샷은 그대로.
     *
     * @return 미룬 행 수
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public int deferForBalance(Long settlementId, LocalDate today, LocalDate nextBusinessDay, LocalDateTime now) {
        int deferred = 0;
        for (SettlementPayout payout : payoutRepository.findScheduledDue(settlementId, today)) {
            if (payoutRepository.updateStatusWhere(payout.getId(), EnumSet.of(PayoutStatus.SCHEDULED), PayoutStatus.SCHEDULED,
                    nextBusinessDay) == 1) {
                deferred++;
            }
        }
        if (deferred > 0) {
            historyRecorder.recordBySystem(settlementId, SettlementEventType.PAYOUT_DEFERRED,
                    "잔액 부족 · %d행 · %s 로 연기".formatted(deferred, nextBusinessDay), now);
        }
        return deferred;
    }

    /** 셀러가 대기(KYC 등)로 바뀌었다(토스 설계서 4-3) — 예정 행을 보류로. 수취자 알림. */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void blockScheduled(Long settlementId, Long payoutId, SettlementPayee payee, String code, String reason,
                               LocalDateTime now) {
        if (payoutRepository.blockWithReason(payoutId, code, reason) == 1) {
            historyRecorder.recordBySystem(settlementId, SettlementEventType.PAYOUT_BLOCKED, "%s · %s · %s".formatted(
                    payee.getLabel(), PayoutBlockReason.fromCode(code).map(PayoutBlockReason::getLabel).orElse(code), reason), now);
            notifier.partnerVerificationRequired(settlementId, payee, code);
        }
    }

    /**
     * 정산 상태 재파생(0-4) — 수취자 행 전부 PAID · NOT_APPLICABLE → PAID(공구 SETTLED) · 하나라도 FAILED → PAYOUT_FAILED ·
     * 실패가 다 풀렸으면(재분배) PAYOUT_SCHEDULED. 호출자 트랜잭션에 합류한다.
     */
    @Transactional(propagation = Propagation.MANDATORY)
    public void derive(Long settlementId, LocalDateTime now) {
        Settlement settlement = settlementRepository.findForUpdate(settlementId)
                .orElseThrow(() -> new BusinessException(ErrorCode.SETTLEMENT_NOT_FOUND));
        SettlementStatus current = settlement.getStatus();
        if (current != SettlementStatus.PAYOUT_SCHEDULED && current != SettlementStatus.PAYOUT_FAILED) {
            return;
        }
        List<SettlementPayout> payouts = payoutRepository.findBySettlementId(settlementId);
        boolean allSettled = payouts.stream().allMatch(p -> p.getStatus().isSettled());
        boolean anyFailed = payouts.stream().anyMatch(p -> p.getStatus() == PayoutStatus.FAILED);
        if (allSettled) {
            if (settlementRepository.transition(settlementId, EnumSet.of(current), SettlementStatus.PAID) != 1) {
                throw new BusinessException(ErrorCode.SETTLEMENT_STATE_CHANGED);
            }
            settlement.applyPaid(now);
            markGroupBuySettled(settlement.getGroupBuyId(), now);
            log.info("정산 지급 완료 - settlementId: {}", settlementId);
        } else if (anyFailed && current == SettlementStatus.PAYOUT_SCHEDULED) {
            if (settlementRepository.transition(settlementId, EnumSet.of(current), SettlementStatus.PAYOUT_FAILED) == 1) {
                settlement.applyDerivedStatus(SettlementStatus.PAYOUT_FAILED);
            }
        } else if (!anyFailed && current == SettlementStatus.PAYOUT_FAILED) {
            if (settlementRepository.transition(settlementId, EnumSet.of(current),
                    SettlementStatus.PAYOUT_SCHEDULED) == 1) {
                settlement.applyDerivedStatus(SettlementStatus.PAYOUT_SCHEDULED);
            }
        }
    }

    /**
     * 보류 재판정(5-4) — 비사업자의 주민등록번호가 등록되면 인플루언서 행 {@code BLOCKED → SCHEDULED(예정일 = max(지급 예정일, 오늘))}.
     * 사업자 건(세금계산서 확인)은 운영자 대조(M4)가 푼다.
     *
     * @return 풀었으면 true
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public boolean releaseIfUnblocked(Long settlementId, Long payoutId, LocalDate today) {
        Settlement settlement = settlementRepository.findForUpdate(settlementId).orElse(null);
        if (settlement == null || settlement.isBusinessCreator() || blockPolicy.isBlocked(settlement)) {
            return false;
        }
        LocalDate due = settlement.getPayoutDueDate() == null || settlement.getPayoutDueDate().isBefore(today)
                ? today : settlement.getPayoutDueDate();
        return payoutRepository.updateStatusWhere(payoutId, EnumSet.of(PayoutStatus.BLOCKED), PayoutStatus.SCHEDULED,
                due) == 1;
    }

    private void markGroupBuySettled(Long groupBuyId, LocalDateTime paidAt) {
        GroupBuy groupBuy = groupBuyRepository.findById(groupBuyId).orElse(null);
        if (groupBuy == null || groupBuy.getStatus() != GroupBuyStatus.ENDED) {
            // 돈은 이미 나갔다 — 공구 전이 실패로 지급 결과를 되돌리지 않는다.
            log.warn("정산 지급 완료 · 공구 SETTLED 전이 건너뜀 - groupBuyId: {}, status: {}", groupBuyId,
                    groupBuy == null ? null : groupBuy.getStatus());
            return;
        }
        groupBuyCommandService.markSettled(groupBuyId, paidAt);
    }

    /** 수취자의 현재 등록 계좌 — 지시 · 재분배(현재 회원 정보)의 스냅샷 원천. 플랫폼은 내부 계정. */
    public Account currentAccount(Settlement settlement, SettlementPayee payee) {
        return switch (payee) {
            case BRAND -> accountOf(settlement.getMarket().getSeller());
            case CREATOR -> accountOf(settlement.getCreator());
            case PLATFORM -> new Account(PLATFORM_ACCOUNT, null, PLATFORM_ACCOUNT);
        };
    }

    static Account accountOf(Seller seller) {
        return seller == null ? new Account(null, null, null)
                : new Account(seller.getBankName(), seller.getAccountNumber(), seller.getAccountHolder());
    }

    private static Account accountOf(Creator creator) {
        return creator == null ? new Account(null, null, null)
                : new Account(creator.getBankName(), creator.getAccountNumber(), creator.getRealName());
    }
}
