package showroomz.api.admin.settlement.service;

import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import showroomz.api.admin.common.AdminOperatorResolver;
import showroomz.api.admin.settlement.dto.AdminSettlementDto;
import showroomz.api.common.settlement.dto.SettlementPartyDto;
import showroomz.api.common.settlement.service.SettlementPartyViews;
import showroomz.domain.contract.entity.Contract;
import showroomz.domain.settlement.adjustment.port.SettlementAdjustmentPort;
import showroomz.domain.settlement.adjustment.service.SettlementAdjustmentReader;
import showroomz.domain.settlement.adjustment.service.SettlementAdjustmentReader.AdjustmentSummary;
import showroomz.domain.settlement.adjustment.type.SettlementParty;
import showroomz.domain.settlement.entity.Settlement;
import showroomz.domain.settlement.entity.SettlementHistory;
import showroomz.domain.settlement.entity.SettlementItem;
import showroomz.domain.settlement.entity.SettlementPayout;
import showroomz.domain.settlement.repository.SettlementHistoryRepository;
import showroomz.domain.settlement.repository.SettlementItemRepository;
import showroomz.domain.settlement.service.PayoutBlockPolicy;
import showroomz.domain.settlement.service.SettlementPayoutTransitions;
import showroomz.domain.settlement.type.PayoutStatus;
import showroomz.domain.settlement.type.SettlementActorType;
import showroomz.domain.settlement.type.SettlementPayee;
import showroomz.domain.settlement.type.SettlementStatus;
import showroomz.global.config.properties.SettlementProperties;
import showroomz.global.utils.PersonalDataCipher;

import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * 07b 상세 조립(44 어드민 설계서 7-4) — 블록마다 원천이 다르다(정산 행 · 수취자 행 · 명세 · 이력 · 조정 협의 · 계약).
 * {@link #actions} 판정은 이 한 곳이고 커맨드가 다시 검사한다(거래 관리와 같은 규칙).
 */
@Component
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class AdminSettlementDetailAssembler {

    static final String PLATFORM_NAME = "SHOWROOMZ";
    private static final int ITEM_PREVIEW_SIZE = 5;

    private final SettlementItemRepository itemRepository;
    private final SettlementHistoryRepository historyRepository;
    private final SettlementPartyViews views;
    private final SettlementAdjustmentReader adjustmentReader;
    private final SettlementAdjustmentPort adjustmentPort;
    private final SettlementPayoutTransitions payoutTransitions;
    private final PayoutBlockPolicy blockPolicy;
    private final AdminOperatorResolver operators;
    private final PersonalDataCipher cipher;
    private final SettlementProperties properties;

    public AdminSettlementDto.Detail detail(Settlement s) {
        boolean confirmed = s.getStatus().isConfirmed();
        Map<SettlementPayee, SettlementPayout> payouts = views.payoutsOf(s.getId());
        AdjustmentSummary adjustment = adjustmentReader.findBySettlementId(s.getId()).orElse(null);
        Object[] totals = first(itemRepository.totalsOf(s.getId()));
        long orderCount = totals == null ? 0 : ((Number) totals[3]).longValue();
        return new AdminSettlementDto.Detail(s.getId(), s.getSettlementNumber(), s.getStatus(),
                s.getStatus().getLabel(), s.getStatus().getTone(),
                new AdminSettlementDto.Overview(s.getGroupBuyId(), s.getGroupBuy().getGroupBuyNumber(),
                        s.getContract().getTitle(), s.getContractId(), s.getMarketId(), s.getMarket().getMarketName(),
                        s.getCreatorId(), s.getCreator().getShowroomName(), s.getCreatorBusinessType(),
                        s.getPeriodStartAt(), s.getPeriodEndAt(), s.getOrdersClosedAt(), s.getCreatedAt(),
                        orderCount, orderCount),
                stage(s, adjustment), breakdown(s), List.of(), adjustment(s, adjustment),
                confirmed ? payouts(s, payouts) : null, List.of(), fixedFee(s.getContract()), items(s, totals),
                rail(s, payouts, adjustment), actions(s, payouts), history(s));
    }

    /** 20b 이슈 패널 보강(7-9) — 상세의 rail + adjustment. */
    public AdminSettlementDto.ByThread byThread(Settlement s, AdjustmentSummary adjustment) {
        return new AdminSettlementDto.ByThread(s.getId(), s.getSettlementNumber(),
                rail(s, views.payoutsOf(s.getId()), adjustment), adjustment(s, adjustment));
    }

    // ------------------------------------------------------------------ 블록

    private static AdminSettlementDto.Stage stage(Settlement s, AdjustmentSummary adjustment) {
        String current = switch (s.getStatus()) {
            case REVIEWING -> "REVIEW";
            case ADJUSTING -> "ADJUSTMENT";
            case PAYOUT_SCHEDULED, PAYOUT_FAILED -> "CONFIRMED";
            case PAID -> "PAID";
        };
        return new AdminSettlementDto.Stage(current, s.getStatus().isConfirmed() && adjustment == null);
    }

    private static AdminSettlementDto.Breakdown breakdown(Settlement s) {
        AdminSettlementDto.BrandAxis brand = new AdminSettlementDto.BrandAxis(s.getGrossOrderAmount(),
                new SettlementPartyDto.AmountCount(s.getCancelDeduction(), s.getCancelCount()),
                new SettlementPartyDto.AmountCount(s.getReturnDeduction(), s.getReturnCount()),
                new SettlementPartyDto.AmountCount(s.getDeliveryExceptionDeduction(), s.getDeliveryExceptionCount()),
                s.getConfirmedSalesAmount(),
                new AdminSettlementDto.RateAmount(s.getPgFeeRate(), s.getPgFeeAmount()),
                new AdminSettlementDto.RateAmount(s.getPlatformFeeRate(), s.getPlatformFeeAmount()),
                s.getOriginalRewardAmount(), s.getRewardAmount(),
                new AdminSettlementDto.RateAmount(s.getRewardVatRate(), s.getRewardVatAmount()),
                new SettlementPartyDto.AmountCount(s.getReshipFeeAmount(), s.getReshipCount()),
                s.getConsumerDeliveryFeeAmount(), s.getBrandPayoutBeforeClawback(), s.getBrandClawbackAmount(),
                s.getBrandPayoutAmount());
        AdminSettlementDto.CreatorAxis creator = new AdminSettlementDto.CreatorAxis(s.getCreatorBusinessType(),
                s.getRewardAmount(), s.getRewardClawbackAmount(),
                s.isBusinessCreator() ? null : new AdminSettlementDto.Withholding(s.getWithholdingAmount(),
                        s.getWithholdingIncomeRate(), s.getWithholdingLocalRate()),
                s.getCreatorVatAmount(), s.getCreatorPayoutAmount());
        return new AdminSettlementDto.Breakdown(brand, creator, s.getPlatformShareAmount());
    }

    private AdminSettlementDto.Adjustment adjustment(Settlement s, AdjustmentSummary a) {
        if (a == null) {
            return null;
        }
        String brandName = s.getMarket().getMarketName();
        String showroomName = s.getCreator().getShowroomName();
        return new AdminSettlementDto.Adjustment(a.adjustmentId(), a.threadId(), a.status().name(),
                a.status().getLabel(), a.requesterType().name(), a.openedAt(), a.deadlineAt(),
                a.remainingBusinessDays(), a.originalRewardAmount(), a.maxRewardAmount(), a.agreedRewardAmount(),
                a.finalRewardAmount(), a.closedAt(),
                a.proposals().stream().map(p -> {
                    SettlementAdjustmentPort.AmountPreview preview = adjustmentPort.preview(s.getId(), p.rewardAmount());
                    return new AdminSettlementDto.AdjustmentProposal(p.proposalId(), p.seq(), p.proposerType().name(),
                            p.proposerType() == SettlementParty.SELLER ? brandName : showroomName, p.proposedAt(),
                            p.rewardAmount(), p.reason(), p.status().name(), p.status().getLabel(), p.respondedAt(),
                            new AdminSettlementDto.ProposalPreview(preview.creatorNetAmount(),
                                    preview.brandPayoutAmount()));
                }).toList());
    }

    private AdminSettlementDto.Payouts payouts(Settlement s, Map<SettlementPayee, SettlementPayout> payouts) {
        List<AdminSettlementDto.Payout> rows = List.of(SettlementPayee.BRAND, SettlementPayee.CREATOR,
                        SettlementPayee.PLATFORM).stream()
                .filter(payouts::containsKey)
                .map(payee -> payout(s, payouts.get(payee)))
                .toList();
        long payoutTotal = payouts.values().stream().mapToLong(SettlementPayout::getAmount).sum();
        long total = payoutTotal + s.getPgFeeAmount() + s.getWithholdingAmount();
        long inflow = s.getConfirmedSalesAmount() + s.getReshipFeeAmount() + s.getConsumerDeliveryFeeAmount();
        return new AdminSettlementDto.Payouts(rows, s.getPgFeeAmount(),
                new AdminSettlementDto.Check(total, inflow, s.getConfirmedSalesAmount(), s.getWithholdingAmount(),
                        total == inflow));
    }

    /** 계좌 — 지시 뒤는 스냅샷, 지시 전은 회원 정보의 현재 계좌(어드민은 전체 노출 · 기본정보 정책). */
    private AdminSettlementDto.Payout payout(Settlement s, SettlementPayout p) {
        String bankName;
        String accountNumber;
        String holder;
        String source;
        if (p.hasAccountSnapshot()) {
            bankName = p.getBankName();
            accountNumber = p.getAccountNumberEnc() == null ? null : cipher.decrypt(p.getAccountNumberEnc());
            holder = p.getAccountHolder();
            source = "SNAPSHOT";
        } else if (p.getPayee() == SettlementPayee.PLATFORM) {
            bankName = null;
            accountNumber = null;
            holder = PLATFORM_NAME;
            source = "NONE";
        } else {
            SettlementPayoutTransitions.Account current = payoutTransitions.currentAccount(s, p.getPayee());
            bankName = current.bankName();
            accountNumber = current.accountNumber();
            holder = current.holder();
            source = current.isMissing() ? "NONE" : "CURRENT_PROFILE";
        }
        return new AdminSettlementDto.Payout(p.getId(), p.getPayee(), p.getPayee().getLabel(), payeeName(s, p.getPayee()),
                p.getAmount(), bankName, accountNumber, holder, source, p.getStatus(), p.getStatus().getLabel(),
                p.getStatus().getTone(), p.getDueDate(), p.getRequestedAt(), p.getPaidAt(), p.getFailedAt(),
                p.getFailCode(), p.getFailReason(), p.getPgReference(), p.getAttempt());
    }

    private static AdminSettlementDto.FixedFee fixedFee(Contract contract) {
        if (contract == null) {
            return null;
        }
        return new AdminSettlementDto.FixedFee(contract.getId(), contract.getFixedFeeAmount(),
                contract.getFixedFeeTrigger() == null ? null : contract.getFixedFeeTrigger().name(),
                contract.getFixedFeeTrigger() == null ? null : contract.getFixedFeeTrigger().getLabel());
    }

    private AdminSettlementDto.Items items(Settlement s, Object[] totals) {
        List<SettlementItem> preview = itemRepository.findPageBySettlementId(s.getId(),
                PageRequest.of(0, ITEM_PREVIEW_SIZE)).getContent();
        long paid = totals == null ? 0 : ((Number) totals[0]).longValue();
        long settled = totals == null ? 0 : ((Number) totals[1]).longValue();
        long itemReward = totals == null ? 0 : ((Number) totals[2]).longValue();
        return new AdminSettlementDto.Items(itemRepository.countBySettlementId(s.getId()), s.getReturnCount(),
                s.getReshipCount(), preview.stream().map(AdminSettlementDetailAssembler::item).toList(),
                new AdminSettlementDto.ItemsFooter(paid, settled, itemReward, s.getRewardAmount()));
    }

    static AdminSettlementDto.Item item(SettlementItem i) {
        return new AdminSettlementDto.Item(i.getId(), i.getOrderId(), i.getDeliveryGroupId(), i.getOrderNumber(),
                i.getSubOrderNumber(), i.getConsumerNameMasked(), i.getProductName(), i.getOptionName(),
                i.getQuantity(), i.getReturnedQuantity(), i.getSettledQuantity(), i.getUnitPrice(), i.getPaidAmount(),
                i.getStatus(), i.getStatus().getLabel(), i.getSettledAmount(), i.getRewardRate(), i.getRewardAmount());
    }

    private AdminSettlementDto.Rail rail(Settlement s, Map<SettlementPayee, SettlementPayout> payouts,
                                         AdjustmentSummary adjustment) {
        boolean confirmed = s.getStatus().isConfirmed();
        SettlementPayout creatorPayout = payouts.get(SettlementPayee.CREATOR);
        List<AdminSettlementDto.BlockReason> blockReasons = confirmed && creatorPayout != null
                && creatorPayout.getStatus() == PayoutStatus.BLOCKED
                ? blockPolicy.blockReasons(s).stream()
                .map(r -> new AdminSettlementDto.BlockReason(r.name(), r.getLabel())).toList()
                : List.of();
        SettlementPayout failed = s.getStatus() != SettlementStatus.PAYOUT_FAILED ? null
                : payouts.values().stream().filter(p -> p.getStatus() == PayoutStatus.FAILED)
                .min(Comparator.comparing(SettlementPayout::getFailedAt, Comparator.nullsLast(Comparator.naturalOrder())))
                .orElse(null);
        return new AdminSettlementDto.Rail(s.getStatus(), s.getStatus().getLabel(), s.getStatus().getTone(),
                s.getConfirmedSalesAmount(), s.getBrandPayoutAmount(), s.getCreatorPayoutAmount(), s.getReviewDueAt(),
                adjustment == null ? null : adjustment.deadlineAt(), s.getConfirmedAt(), s.getConfirmReason(),
                s.getConfirmReason() == null ? null : s.getConfirmReason().getLabel(), s.getPayoutDueDate(),
                confirmed ? views.payoutBasis(s.getId()) : null, s.getPaidAt(), blockReasons,
                failed == null ? null : new AdminSettlementDto.FailedPayout(failed.getId(), failed.getPayee(),
                        failed.getPayee().getLabel(), failed.getAmount(), failed.getFailedAt(), failed.getFailCode(),
                        failed.getFailReason(), failed.getAttempt(), properties.getPayoutRetryLimit()));
    }

    /** 버튼 판정 — 커맨드가 같은 식을 다시 검사한다. 증빙 두 버튼은 증빙 단계(V181) 전까지 false. */
    AdminSettlementDto.Actions actions(Settlement s, Map<SettlementPayee, SettlementPayout> payouts) {
        return new AdminSettlementDto.Actions(canRedistribute(s, payouts.values()), false, false,
                s.getStatus().isConfirmed());
    }

    boolean canRedistribute(Settlement s, java.util.Collection<SettlementPayout> payouts) {
        return s.getStatus() == SettlementStatus.PAYOUT_FAILED && payouts.stream()
                .anyMatch(p -> p.getStatus() == PayoutStatus.FAILED
                        && p.getAttempt() < properties.getPayoutRetryLimit());
    }

    private List<AdminSettlementDto.History> history(Settlement s) {
        List<SettlementHistory> rows = historyRepository.findLatestFirst(s.getId());
        Map<Long, String> operatorNames = operators.namesOf(rows.stream()
                .filter(h -> h.getActorType() == SettlementActorType.ADMIN).map(SettlementHistory::getActorId)
                .filter(Objects::nonNull).toList());
        return rows.stream().map(h -> new AdminSettlementDto.History(h.getEventType(), h.getEventType().getLabel(),
                h.getActorType(), actorLabel(s, h, operatorNames), h.getDetail(), h.getOccurredAt())).toList();
    }

    private static String actorLabel(Settlement s, SettlementHistory h, Map<Long, String> operatorNames) {
        return switch (h.getActorType()) {
            case ADMIN -> h.getActorId() == null ? h.getActorType().getLabel()
                    : operatorNames.getOrDefault(h.getActorId(), h.getActorType().getLabel());
            case SELLER -> s.getMarket().getMarketName();
            case CREATOR -> s.getCreator().getShowroomName();
            case SYSTEM, PG -> h.getActorType().getLabel();
        };
    }

    static String payeeName(Settlement s, SettlementPayee payee) {
        return switch (payee) {
            case BRAND -> s.getMarket().getMarketName();
            case CREATOR -> s.getCreator().getShowroomName();
            case PLATFORM -> PLATFORM_NAME;
        };
    }

    private static Object[] first(List<Object[]> rows) {
        return rows.isEmpty() ? null : rows.get(0);
    }
}
