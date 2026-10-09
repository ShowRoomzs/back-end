package showroomz.domain.settlement.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import showroomz.domain.contract.entity.ContractItem;
import showroomz.domain.contract.repository.ContractItemRepository;
import showroomz.domain.groupbuy.entity.GroupBuy;
import showroomz.domain.groupbuy.repository.GroupBuyRepository;
import showroomz.domain.groupbuy.service.GroupBuyCommandService;
import showroomz.domain.groupbuy.service.port.GroupBuySalesReader;
import showroomz.domain.groupbuy.type.GroupBuyStatus;
import showroomz.domain.member.creator.type.CreatorBusinessType;
import showroomz.domain.order.entity.OrderRefundTask;
import showroomz.domain.order.repository.OrderClaimChargeRepository;
import showroomz.domain.order.repository.OrderProductRepository;
import showroomz.domain.order.repository.OrderRefundTaskRepository;
import showroomz.domain.order.repository.SettlementSourceRow;
import showroomz.domain.order.service.BusinessDayCalculator;
import showroomz.domain.order.type.ClaimChargeStatus;
import showroomz.domain.order.type.FulfillmentStatus;
import showroomz.domain.order.type.OrderCancelType;
import showroomz.domain.order.type.OrderProductStatus;
import showroomz.domain.order.type.RefundTaskSource;
import showroomz.domain.settlement.entity.Settlement;
import showroomz.domain.settlement.entity.SettlementItem;
import showroomz.domain.settlement.entity.SettlementPayout;
import showroomz.domain.settlement.repository.SettlementItemRepository;
import showroomz.domain.settlement.repository.SettlementPayoutRepository;
import showroomz.domain.settlement.repository.SettlementRepository;
import showroomz.domain.settlement.type.SettlementEventType;
import showroomz.domain.settlement.type.SettlementItemStatus;
import showroomz.domain.settlement.type.SettlementPayee;
import showroomz.global.config.properties.SettlementProperties;
import showroomz.global.error.exception.BusinessException;
import showroomz.global.error.exception.ErrorCode;
import showroomz.global.utils.MaskingUtils;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Stream;

/**
 * 정산 생성(44 어드민 설계서 2절) — 공구의 주문이 전부 종결되면 금액을 확정 거래액으로 스냅샷해 {@code REVIEWING} 정산 1건을 만든다.
 *
 * <p>생성 사건은 「마지막 하위주문 종결」이고 <b>배치가 판정한다</b>(0-2) — 종결 경로가 여럿이라 즉시 훅을 두면 모든 경로에 코드가
 * 들어가고 동시 종결에서 경합한다. 종결 판정식은 {@link GroupBuyCommandService#isSettlementReady} 하나다 — 공구 B5 「주문 종결
 * 대기(남은 N건)」와 같은 숫자에서 정산이 생긴다.
 *
 * <p>공구 1건 = 정산 1건 — 동시 실행은 {@code uk_settlement_group_buy}가 떨어뜨린다(배치가 무시한다).
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SettlementGenerationService {

    /** 정산 원천이 되는 하위주문 — 종결된 것만(반송은 반송 완료 환불까지 끝난 것만 — 게이트가 보장한다). */
    private static final Set<FulfillmentStatus> SOURCE_GROUP_STATUSES =
            EnumSet.of(FulfillmentStatus.CONFIRMED, FulfillmentStatus.CANCELLED, FulfillmentStatus.RETURNING);
    /** 소비자가 낸 재발송비 — 브랜드 가산(2-2). */
    private static final Set<ClaimChargeStatus> RESHIP_CHARGE_STATUSES =
            EnumSet.of(ClaimChargeStatus.PAID, ClaimChargeStatus.DEDUCTED, ClaimChargeStatus.COVERED);
    private static final DateTimeFormatter DUE_FORMAT = DateTimeFormatter.ofPattern("MM.dd HH:mm");

    private final GroupBuyRepository groupBuyRepository;
    private final GroupBuyCommandService groupBuyCommandService;
    private final GroupBuySalesReader salesReader;
    private final ContractItemRepository contractItemRepository;
    private final OrderProductRepository orderProductRepository;
    private final OrderRefundTaskRepository refundTaskRepository;
    private final OrderClaimChargeRepository claimChargeRepository;
    private final SettlementRepository settlementRepository;
    private final SettlementItemRepository itemRepository;
    private final SettlementPayoutRepository payoutRepository;
    private final SettlementHistoryRecorder historyRecorder;
    private final SettlementNumberGenerator numberGenerator;
    private final SettlementCalculator calculator;
    private final SettlementClawbackLedger clawbackLedger;
    private final SettlementNotifier notifier;
    private final SettlementProperties properties;
    private final BusinessDayCalculator businessDayCalculator;

    @Transactional(readOnly = true)
    public List<Long> findGroupBuyIdsToGenerate(int limit) {
        return settlementRepository.findGroupBuyIdsToGenerate(PageRequest.of(0, limit));
    }

    /**
     * 공구 하나의 정산 생성 — 행마다 <b>새 트랜잭션</b>(배치의 한 건 실패가 나머지를 막지 않는다). 공구 행을 잠그고 게이트를 다시 본다.
     *
     * @return 만든 정산 id — 아직 종결 전 · 판매 0건 · 이미 있음이면 empty
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public Optional<Long> generate(Long groupBuyId, LocalDateTime now) {
        GroupBuy groupBuy = groupBuyRepository.findForUpdate(groupBuyId).orElse(null);
        if (groupBuy == null || groupBuy.getStatus() != GroupBuyStatus.ENDED
                || settlementRepository.findByGroupBuyId(groupBuyId).isPresent()) {
            return Optional.empty();
        }
        if (!groupBuyCommandService.isSettlementReady(groupBuyId)) {
            return Optional.empty();
        }
        GroupBuySalesReader.GroupBuyOrderClosure closure = salesReader.readClosure(groupBuyId).orElse(null);
        if (closure == null || closure.totalCount() == 0) {
            return Optional.empty(); // 판매 0건 — 정산을 만들지 않는다(공통 결정 #16)
        }
        List<SettlementSourceRow> rows = orderProductRepository
                .findSettlementSourceByGroupBuy(groupBuyId, SOURCE_GROUP_STATUSES).stream()
                .filter(row -> row.groupStatus() != FulfillmentStatus.RETURNING || row.returnCompletedAt() != null)
                .toList();
        if (rows.isEmpty()) {
            return Optional.empty();
        }

        Collect collected = collect(groupBuy, rows);
        SettlementClawbackLedger.Pending pending = clawbackLedger.pendingFor(groupBuy.getMarket().getId(),
                groupBuy.getCreator().getId());
        CreatorBusinessType businessType = groupBuy.getCreator().getBusinessType() == null
                ? CreatorBusinessType.INDIVIDUAL : groupBuy.getCreator().getBusinessType();
        SettlementAmounts amounts = calculator.calculate(new SettlementInput(collected.confirmedSales(),
                collected.reward(), collected.reshipFee(), collected.consumerDeliveryFee(), pending.brandAmount(),
                pending.rewardAmount(), businessType, rates()));
        calculator.verify(amounts);

        LocalDateTime reviewDueAt = businessDayCalculator.dueAt(now, properties.getReviewBusinessDays());
        Settlement settlement = settlementRepository.save(Settlement.builder()
                .settlementNumber(numberGenerator.nextSettlementNumber(now))
                .groupBuy(groupBuy)
                .contract(groupBuy.getContract())
                .market(groupBuy.getMarket())
                .creator(groupBuy.getCreator())
                .creatorBusinessType(businessType)
                .periodStartAt(groupBuy.getStartAt())
                .periodEndAt(groupBuy.getEndedAt() != null ? groupBuy.getEndedAt() : groupBuy.getEndAt())
                .ordersClosedAt(collected.ordersClosedAt() != null ? collected.ordersClosedAt() : now)
                .createdAt(now)
                .reviewDueAt(reviewDueAt)
                .breakdown(collected.breakdown())
                .amounts(amounts)
                .build());
        Long settlementId = settlement.getId();
        itemRepository.saveAll(collected.drafts().stream().map(draft -> draft.toItem(settlementId)).toList());
        payoutRepository.saveAll(List.of(
                SettlementPayout.waiting(settlementId, SettlementPayee.BRAND, amounts.brandPayoutAmount()),
                SettlementPayout.waiting(settlementId, SettlementPayee.CREATOR, amounts.creatorPayoutAmount()),
                SettlementPayout.waiting(settlementId, SettlementPayee.PLATFORM, amounts.platformShareAmount())));
        clawbackLedger.applyTo(settlement, amounts, now);
        historyRecorder.recordBySystem(settlementId, SettlementEventType.CREATED,
                "공구 종료 · 주문 %d건 종결 · 금액 공개 · 확인 기간 %s까지".formatted(closure.totalCount(),
                        reviewDueAt.format(DUE_FORMAT)), now);
        groupBuyCommandService.recordSalesFinalized(groupBuyId, now);
        afterCommit(() -> notifier.settlementGenerated(settlementId));
        log.info("정산 생성 - groupBuyId: {}, settlementId: {}, number: {}", groupBuyId, settlementId,
                settlement.getSettlementNumber());
        return Optional.of(settlementId);
    }

    private SettlementRates rates() {
        return new SettlementRates(properties.getPgFeeRate(), properties.getPlatformFeeRate(),
                properties.getRewardVatRate(), properties.getWithholdingIncomeRate(),
                properties.getWithholdingLocalRate());
    }

    // ------------------------------------------------------------------ 입력 수집(2-2 · 2-3)

    private record Collect(List<Draft> drafts, Settlement.Breakdown breakdown, long confirmedSales, long reward,
                           long reshipFee, long consumerDeliveryFee, LocalDateTime ordersClosedAt) {
    }

    private Collect collect(GroupBuy groupBuy, List<SettlementSourceRow> rows) {
        Long groupBuyId = groupBuy.getId();
        Map<Long, BigDecimal> rewardRates = new HashMap<>();
        for (ContractItem item : contractItemRepository.findWithProductByContractIds(
                List.of(groupBuy.getContract().getId()))) {
            if (item.getProduct() != null && item.getRewardRate() != null) {
                rewardRates.put(item.getProduct().getProductId(), item.getRewardRate());
            }
        }

        List<Draft> drafts = rows.stream().map(Draft::classify).toList();
        Map<Long, Long> deliveryFeeRefunded = applyOperatorRefunds(groupBuyId, drafts);

        long gross = 0, cancel = 0, returned = 0, exception = 0, settled = 0, reward = 0;
        int cancelCount = 0, returnCount = 0, exceptionCount = 0;
        for (Draft draft : drafts) {
            BigDecimal rate = rewardRates.get(draft.row.productId());
            if (rate == null) {
                if (draft.settledAmount > 0) {
                    // 리워드율 없는 매출을 0% 로 간주하면 분쟁이 된다 — 생성 실패 · 다음 회차 재시도(13절 신규 #2).
                    throw new BusinessException(ErrorCode.SETTLEMENT_GENERATION_INCONSISTENT,
                            "계약 항목이 없는 상품의 매출 — groupBuyId=%d · productId=%d".formatted(groupBuyId,
                                    draft.row.productId()));
                }
                rate = BigDecimal.ZERO.setScale(1);
            }
            draft.rewardRate = rate;
            draft.unitReward = calculator.unitReward(draft.unitPrice, rate);
            gross += draft.paidAmount();
            cancel += draft.cancelPart;
            returned += draft.returnPart;
            exception += draft.exceptionPart;
            settled += draft.settledAmount;
            reward += draft.rewardAmount();
            cancelCount += draft.cancelPart > 0 ? 1 : 0;
            returnCount += draft.returnPart > 0 ? 1 : 0;
            exceptionCount += draft.exceptionPart > 0 ? 1 : 0;
        }
        if (gross - cancel - returned - exception != settled) {
            throw new BusinessException(ErrorCode.SETTLEMENT_GENERATION_INCONSISTENT,
                    "확정 거래액 불일치 — groupBuyId=%d · 분해 %d · 명세 %d".formatted(groupBuyId,
                            gross - cancel - returned - exception, settled));
        }

        Map<Long, SettlementSourceRow> groups = new LinkedHashMap<>();
        rows.forEach(row -> groups.putIfAbsent(row.deliveryGroupId(), row));
        long consumerDeliveryFee = groups.values().stream()
                .filter(row -> row.groupStatus() == FulfillmentStatus.CONFIRMED)
                .mapToLong(row -> Math.max(0, nullToZero(row.deliveryFee())
                        - deliveryFeeRefunded.getOrDefault(row.deliveryGroupId(), 0L)))
                .sum();
        LocalDateTime ordersClosedAt = groups.values().stream()
                .flatMap(row -> Stream.of(row.confirmedAt(), row.cancelledAt(), row.returnCompletedAt()))
                .filter(Objects::nonNull)
                .max(Comparator.naturalOrder())
                .orElse(null);

        long reshipFee = 0;
        int reshipCount = 0;
        List<Object[]> charges = claimChargeRepository.sumByGroupBuy(groupBuyId, RESHIP_CHARGE_STATUSES);
        if (!charges.isEmpty() && charges.get(0) != null) {
            reshipCount = charges.get(0)[0] instanceof Number n ? n.intValue() : 0;
            reshipFee = charges.get(0)[1] instanceof Number n ? n.longValue() : 0L;
        }

        return new Collect(drafts, new Settlement.Breakdown(gross, cancel, cancelCount, returned, returnCount,
                exception, exceptionCount, reshipCount), settled, reward, reshipFee, consumerDeliveryFee,
                ordersClosedAt);
    }

    /**
     * 정산 생성 <b>전에</b> 집행된 운영자 사유 환불(06a B5 — 구매확정 후 하자 · 위해성 리콜)을 배송 예외 차감에 더한다(2-2). 06a 환불은
     * 항목 없이 금액만 있으므로 그 하위주문의 반영액에서 항목 순서대로 덜어 낸다 — 단가를 다 덜어 낸 수량만큼 반영 수량이 줄고(리워드도
     * 함께), 부분 금액(보상)은 반영액에서만 빠진다. 항목 반영액을 다 덮고 남은 금액은 그 하위주문 배송비의 환불로 본다.
     *
     * <p>클레임에 묶인 운영자 환불(반려 이의 인용 · 검수 무응답 — {@code source_id = claim_id})은 편입 때 반품 수량에 이미 올라가
     * 반품 차감으로 잡히므로 여기서 다시 빼지 않는다.
     *
     * @return 하위주문 id → 배송비에서 환불된 금액
     */
    private Map<Long, Long> applyOperatorRefunds(Long groupBuyId, List<Draft> drafts) {
        Map<Long, Long> deliveryFeeRefunded = new HashMap<>();
        for (OrderRefundTask task : refundTaskRepository.findDoneByGroupBuyAndSource(groupBuyId,
                RefundTaskSource.OPERATOR_REASON)) {
            if (task.getSourceId() != null) {
                continue;
            }
            Long groupId = task.getDeliveryGroup().getId();
            long remaining = task.getRefundAmount() == null ? 0 : task.getRefundAmount();
            for (Draft draft : drafts) {
                if (remaining <= 0) {
                    break;
                }
                if (!draft.row.deliveryGroupId().equals(groupId) || draft.settledAmount <= 0) {
                    continue;
                }
                long allocated = Math.min(remaining, draft.settledAmount);
                draft.absorbRefund(allocated);
                remaining -= allocated;
            }
            if (remaining > 0) {
                deliveryFeeRefunded.merge(groupId, remaining, Long::sum);
            }
        }
        return deliveryFeeRefunded;
    }

    private static long nullToZero(Integer value) {
        return value == null ? 0 : value;
    }

    private static void afterCommit(Runnable action) {
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            action.run();
            return;
        }
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                action.run();
            }
        });
    }

    /** 명세 한 줄의 작업본 — 분류(2-2 ①) 뒤 운영자 환불을 덜어 내고 리워드율을 붙여 {@link SettlementItem}이 된다. */
    private static final class Draft {

        private final SettlementSourceRow row;
        private final long unitPrice;
        private final int quantity;
        private final int returnedQuantity;
        private SettlementItemStatus status;
        private int settledQuantity;
        private long settledAmount;
        private long cancelPart;
        private long returnPart;
        private long exceptionPart;
        private BigDecimal rewardRate;
        private long unitReward;

        private Draft(SettlementSourceRow row) {
            this.row = row;
            this.unitPrice = row.price() == null ? 0 : row.price();
            this.quantity = row.quantity() == null ? 0 : row.quantity();
            this.returnedQuantity = Math.min(quantity,
                    row.returnedQuantity() == null ? 0 : Math.max(0, row.returnedQuantity()));
        }

        /**
         * 명세 상태 · 반영 수량(2-2 ① 표). 취소는 항목의 취소 유형(없으면 하위주문의 것)으로 가른다 — 분실 처리만 배송 예외다.
         * 반송 완료 하위주문은 전 항목이 배송 예외다(반송은 환불로 종결).
         */
        static Draft classify(SettlementSourceRow row) {
            Draft draft = new Draft(row);
            long paid = draft.paidAmount();
            boolean itemCancelled = row.itemStatus() == OrderProductStatus.CANCELLED
                    || (row.groupStatus() == FulfillmentStatus.CANCELLED && row.itemStatus() != OrderProductStatus.RETURNED);
            if (itemCancelled) {
                OrderCancelType type = row.itemCancelType() != null ? row.itemCancelType() : row.groupCancelType();
                if (type == OrderCancelType.LOST) {
                    draft.status = SettlementItemStatus.DELIVERY_EXCEPTION;
                    draft.exceptionPart = paid;
                } else {
                    draft.status = SettlementItemStatus.CANCELLED;
                    draft.cancelPart = paid;
                }
            } else if (row.groupStatus() == FulfillmentStatus.RETURNING) {
                draft.status = SettlementItemStatus.DELIVERY_EXCEPTION;
                draft.exceptionPart = paid;
            } else if (row.itemStatus() == OrderProductStatus.RETURNED) {
                draft.status = SettlementItemStatus.RETURNED;
                draft.returnPart = paid;
            } else {
                draft.returnPart = draft.unitPrice * draft.returnedQuantity;
                draft.settledQuantity = draft.quantity - draft.returnedQuantity;
                draft.status = draft.returnedQuantity > 0 ? SettlementItemStatus.PARTIAL_RETURNED
                        : SettlementItemStatus.CONFIRMED;
            }
            draft.settledAmount = paid - draft.cancelPart - draft.returnPart - draft.exceptionPart;
            return draft;
        }

        long paidAmount() {
            return unitPrice * quantity;
        }

        long rewardAmount() {
            return unitReward * settledQuantity;
        }

        /** 운영자 사유 환불 금액을 덜어 낸다 — 단가 단위로 다 빠진 수량만 반영 수량에서 뺀다. 전액이 빠지면 배송 예외. */
        void absorbRefund(long amount) {
            settledAmount -= amount;
            exceptionPart += amount;
            int units = unitPrice <= 0 ? settledQuantity : (int) Math.min(settledQuantity, amount / unitPrice);
            settledQuantity -= units;
            if (settledAmount == 0) {
                settledQuantity = 0;
                status = SettlementItemStatus.DELIVERY_EXCEPTION;
            }
        }

        SettlementItem toItem(Long settlementId) {
            String consumerName = row.consumerName() != null && !row.consumerName().isBlank()
                    ? row.consumerName() : row.consumerNickname();
            return SettlementItem.builder()
                    .settlementId(settlementId)
                    .orderId(row.orderId())
                    .deliveryGroupId(row.deliveryGroupId())
                    .orderProductId(row.orderProductId())
                    .orderNumber(row.orderNumber())
                    .subOrderNumber(row.subOrderNumber())
                    .consumerUserId(row.consumerUserId())
                    .consumerNameMasked(consumerName == null ? null : MaskingUtils.maskName(consumerName))
                    .productId(row.productId())
                    .productName(row.productName())
                    .optionName(row.optionName())
                    .quantity(quantity)
                    .returnedQuantity(returnedQuantity)
                    .settledQuantity(settledQuantity)
                    .unitPrice(unitPrice)
                    .paidAmount(paidAmount())
                    .settledAmount(settledAmount)
                    .rewardRate(rewardRate)
                    .unitReward(unitReward)
                    .rewardAmount(rewardAmount())
                    .status(status)
                    .build();
        }
    }
}
