package showroomz.domain.settlement.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import showroomz.domain.groupbuy.entity.GroupBuy;
import showroomz.domain.groupbuy.repository.GroupBuyRepository;
import showroomz.domain.groupbuy.service.GroupBuyCommandService;
import showroomz.domain.groupbuy.type.GroupBuyStatus;
import showroomz.domain.member.creator.entity.Creator;
import showroomz.domain.member.seller.entity.Seller;
import showroomz.domain.settlement.entity.Settlement;
import showroomz.domain.settlement.entity.SettlementPayout;
import showroomz.domain.settlement.port.SettlementPayoutGateway.LineResult;
import showroomz.domain.settlement.port.SettlementPayoutGateway.PayoutCommand;
import showroomz.domain.settlement.port.SettlementPayoutGateway.PayoutLine;
import showroomz.domain.settlement.port.SettlementPayoutGateway.PayoutResult;
import showroomz.domain.settlement.repository.SettlementPayoutRepository;
import showroomz.domain.settlement.repository.SettlementRepository;
import showroomz.domain.settlement.type.PayoutStatus;
import showroomz.domain.settlement.type.SettlementActorType;
import showroomz.domain.settlement.type.SettlementEventType;
import showroomz.domain.settlement.type.SettlementPayee;
import showroomz.domain.settlement.type.SettlementStatus;
import showroomz.global.error.exception.BusinessException;
import showroomz.global.error.exception.ErrorCode;
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

    /** 지급 계좌 — 지시 시점 스냅샷의 원천(회원 정보). 플랫폼은 내부 계정이다. */
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
        Account brand = accountOf(settlement.getMarket().getSeller());
        Account creator = accountOf(settlement.getCreator());
        String settlementNumber = settlement.getSettlementNumber();

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
            Account account = switch (payout.getPayee()) {
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
            if (payoutRepository.markRequested(payout.getId(), account.bankName(), cipher.encrypt(account.accountNumber()),
                    account.holder(), now) == 1) {
                lines.add(new PayoutLine(payout.getId(), payout.getPayee(), payout.getAmount(), account.bankName(),
                        account.accountNumber(), account.holder()));
                historyRecorder.recordBySystem(settlementId, SettlementEventType.PAYOUT_REQUESTED,
                        "%s · %,d원 지급 지시".formatted(payout.getPayee().getLabel(), payout.getAmount()), now);
            }
        }
        return new PayoutCommand(settlementId, settlementNumber, lines);
    }

    /** 게이트웨이 결과 반영 — 행마다 조건부 UPDATE · 이력(PG) · 정산 상태 재파생. */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void applyResults(Long settlementId, PayoutCommand command, PayoutResult result, LocalDateTime now) {
        Map<Long, PayoutLine> sent = command.lines().stream()
                .collect(Collectors.toMap(PayoutLine::payoutId, Function.identity()));
        boolean creatorPaid = false;
        for (LineResult line : result.lines()) {
            PayoutLine request = sent.get(line.payoutId());
            if (request == null) {
                continue;
            }
            switch (line.outcome()) {
                case PAID -> {
                    if (payoutRepository.markPaid(line.payoutId(), line.pgReference(), now) == 1) {
                        historyRecorder.record(settlementId, SettlementEventType.PAYOUT_PAID, SettlementActorType.PG,
                                null, "%s · %,d원 · %s".formatted(request.payee().getLabel(), request.amount(),
                                        line.pgReference()), now);
                        notifier.settlementPaid(settlementId, request.payee());
                        creatorPaid |= request.payee() == SettlementPayee.CREATOR;
                    }
                }
                case FAILED -> {
                    if (payoutRepository.markFailed(line.payoutId(), line.pgReference(), line.failCode(),
                            line.failReason(), now) == 1) {
                        historyRecorder.record(settlementId, SettlementEventType.PAYOUT_FAILED, SettlementActorType.PG,
                                null, "%s · %s".formatted(request.payee().getLabel(),
                                        line.failReason() == null ? "분배 실패" : line.failReason()), now);
                        notifier.payoutFailed(settlementId, request.payee());
                    }
                }
                case REQUESTED -> payoutRepository.recordReference(line.payoutId(), line.pgReference());
            }
        }
        if (creatorPaid) {
            taxDocumentHook.onCreatorPaid(settlementId, now);
        }
        derive(settlementId, now);
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

    private static Account accountOf(Seller seller) {
        return seller == null ? new Account(null, null, null)
                : new Account(seller.getBankName(), seller.getAccountNumber(), seller.getAccountHolder());
    }

    private static Account accountOf(Creator creator) {
        return creator == null ? new Account(null, null, null)
                : new Account(creator.getBankName(), creator.getAccountNumber(), creator.getRealName());
    }
}
