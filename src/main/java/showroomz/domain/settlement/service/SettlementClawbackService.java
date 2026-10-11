package showroomz.domain.settlement.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import showroomz.domain.order.entity.OrderClaim;
import showroomz.domain.order.entity.OrderRefundTask;
import showroomz.domain.order.repository.OrderClaimRepository;
import showroomz.domain.order.repository.OrderRefundTaskRepository;
import showroomz.domain.order.type.OperatorRefundReason;
import showroomz.domain.order.type.RefundTaskSource;
import showroomz.domain.order.type.RefundTaskStatus;
import showroomz.domain.settlement.entity.Settlement;
import showroomz.domain.settlement.entity.SettlementClawback;
import showroomz.domain.settlement.entity.SettlementItem;
import showroomz.domain.settlement.entity.SettlementTaxDocument;
import showroomz.domain.settlement.repository.SettlementClawbackRepository;
import showroomz.domain.settlement.repository.SettlementItemRepository;
import showroomz.domain.settlement.repository.SettlementRepository;
import showroomz.domain.settlement.repository.SettlementTaxDocumentRepository;
import showroomz.domain.settlement.type.ClawbackSide;
import showroomz.domain.settlement.type.ClawbackStatus;
import showroomz.domain.settlement.type.ClawbackUnrecoverableReason;
import showroomz.domain.settlement.type.SettlementEventType;
import showroomz.domain.settlement.type.TaxDocumentStatus;
import showroomz.domain.settlement.type.TaxDocumentType;
import showroomz.global.error.exception.BusinessException;
import showroomz.global.error.exception.ErrorCode;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 차감(클로백 · 44 어드민 설계서 6절) — 발생(6-1) · 반영(2-5 · 6-2) · 미회수(3-6) · 수정세금계산서(6-4).
 *
 * <p>측별로 독립 회수한다 — 브랜드 측은 같은 마켓의 다음 정산, 인플루언서 측은 같은 인플루언서의 다음 정산(0-8). 회수된 돈은 플랫폼
 * 몫으로 간다(환불은 플랫폼이 먼저 집행했다). 이미 생성된 정산의 금액은 고치지 않는다(스냅샷).
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SettlementClawbackService implements SettlementClawbackLedger {

    /** 차감을 만드는 환불 — 운영자 사유 환불 · 운영자 개설 반품 통과(정산 생성 뒤에도 일어날 수 있는 환불). */
    private static final Set<RefundTaskSource> SOURCES = EnumSet.of(RefundTaskSource.OPERATOR_REASON,
            RefundTaskSource.CLAIM_RETURN_PASSED);
    private static final String CLAIM_RETURN_PASSED_LABEL = "반품 통과(운영자 개설)";

    private final SettlementClawbackRepository clawbackRepository;
    private final SettlementItemRepository itemRepository;
    private final SettlementRepository settlementRepository;
    private final SettlementTaxDocumentRepository documentRepository;
    private final OrderRefundTaskRepository refundTaskRepository;
    private final OrderClaimRepository claimRepository;
    private final SettlementNumberGenerator numberGenerator;
    private final SettlementHistoryRecorder historyRecorder;
    private final SettlementNotifier notifier;

    public static boolean isClawbackSource(RefundTaskSource source) {
        return SOURCES.contains(source);
    }

    public static String reasonLabel(String reason) {
        if (reason == null) {
            return CLAIM_RETURN_PASSED_LABEL;
        }
        try {
            return OperatorRefundReason.valueOf(reason).getLabel();
        } catch (IllegalArgumentException e) {
            return reason;
        }
    }

    // ------------------------------------------------------------------ 6-1 발생

    /**
     * 환불 집행 완료 뒤(별도 트랜잭션) — 이미 생성된 정산의 항목이면 측별 2행 · {@code CLW-NNNN}. 정산이 아직 없으면 아무것도 하지 않는다
     * (생성 시 배송 예외 · 반품 차감으로 잡힌다). 같은 큐 행으로 두 번 불려도 한 번만 만든다.
     *
     * <pre>
     * CREATOR 행 = 단위 리워드 × 환불 수량
     * BRAND 행   = 환불 상품 금액분 − CREATOR 행 − floor(CREATOR 행 × 리워드 부가세율)
     * </pre>
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public List<SettlementClawback> register(Long refundTaskId, LocalDateTime now) {
        if (clawbackRepository.existsByRefundTaskId(refundTaskId)) {
            return List.of();
        }
        OrderRefundTask task = refundTaskRepository.findById(refundTaskId).orElse(null);
        if (task == null || task.getStatus() != RefundTaskStatus.DONE || !isClawbackSource(task.getSource())) {
            return List.of();
        }
        Target target = task.getSourceId() != null ? claimTarget(task) : groupTarget(task);
        if (target == null || target.refundAmount() <= 0) {
            return List.of();
        }
        Settlement origin = settlementRepository.findById(target.settlementId())
                .orElseThrow(() -> new BusinessException(ErrorCode.SETTLEMENT_NOT_FOUND));
        long creatorAmount = target.creatorAmount();
        long creatorVat = BigDecimal.valueOf(creatorAmount).multiply(origin.getRewardVatRate())
                .setScale(0, RoundingMode.DOWN).longValue();
        long brandAmount = Math.max(0, target.refundAmount() - creatorAmount - creatorVat);
        String number = numberGenerator.nextClawbackNumber();
        String reason = task.getReasonCode() == null || task.getSource() == RefundTaskSource.CLAIM_RETURN_PASSED
                ? null : task.getReasonCode().name();
        List<SettlementClawback> created = new ArrayList<>();
        for (ClawbackSide side : List.of(ClawbackSide.BRAND, ClawbackSide.CREATOR)) {
            long amount = side == ClawbackSide.BRAND ? brandAmount : creatorAmount;
            if (amount <= 0) {
                continue;
            }
            created.add(clawbackRepository.save(SettlementClawback.builder()
                    .clawbackNumber(number).seq(1).side(side).originSettlementId(origin.getId())
                    .orderId(target.orderId()).deliveryGroupId(target.deliveryGroupId())
                    .orderProductId(target.orderProductId()).refundTaskId(refundTaskId).reason(reason)
                    .refundAmount(target.refundAmount()).amount(amount).marketId(origin.getMarketId())
                    .creatorId(origin.getCreatorId()).createdAt(now)
                    .build()));
        }
        if (created.isEmpty()) {
            return created;
        }
        historyRecorder.recordBySystem(origin.getId(), SettlementEventType.CLAWBACK_REGISTERED,
                "차감 발생 · %s · %s · 브랜드 −%,d · 인플루언서 −%,d".formatted(number, reasonLabel(reason), brandAmount,
                        creatorAmount), now);
        Long originId = origin.getId();
        AfterCommit.run(() -> notifier.clawbackRegistered(originId, number));
        log.info("정산 차감 발생 - {} · refundTaskId: {} · 브랜드 {} · 인플루언서 {}", number, refundTaskId, brandAmount,
                creatorAmount);
        return created;
    }

    private record Target(Long settlementId, Long orderId, Long deliveryGroupId, Long orderProductId,
                          long refundAmount, long creatorAmount) {
    }

    /** 클레임에 묶인 환불 — 큐 행 → 클레임 → 주문 항목. */
    private Target claimTarget(OrderRefundTask task) {
        OrderClaim claim = claimRepository.findById(task.getSourceId()).orElse(null);
        if (claim == null || claim.getOrderProduct() == null) {
            return null;
        }
        SettlementItem item = itemRepository.findByOrderProductId(claim.getOrderProduct().getId()).orElse(null);
        if (item == null) {
            return null;
        }
        int quantity = claim.getQuantity() == null ? 0 : claim.getQuantity();
        long refund = Math.min(task.getRefundAmount() == null ? 0 : task.getRefundAmount(),
                item.getUnitPrice() * quantity);
        return new Target(item.getSettlementId(), item.getOrderId(), item.getDeliveryGroupId(),
                item.getOrderProductId(), refund, item.getUnitReward() * quantity);
    }

    /**
     * 클레임 없는 운영자 사유 환불(06a B5) — 금액을 하위주문 항목에 정산 반영액 순서대로 나눈다(생성의 배송 예외 처리와 같은 규칙).
     * 단가 단위로 다 빠진 수량만큼 리워드를 회수하고 남는 금액은 배송비분이라 뺀다.
     */
    private Target groupTarget(OrderRefundTask task) {
        List<SettlementItem> items = itemRepository.findByDeliveryGroupId(task.getDeliveryGroup().getId()).stream()
                .sorted(Comparator.comparing(SettlementItem::getId)).toList();
        if (items.isEmpty()) {
            return null;
        }
        long remaining = task.getRefundAmount() == null ? 0 : task.getRefundAmount();
        long refund = 0;
        long creator = 0;
        SettlementItem first = null;
        for (SettlementItem item : items) {
            if (remaining <= 0) {
                break;
            }
            if (item.getSettledAmount() <= 0) {
                continue;
            }
            long allocated = Math.min(remaining, item.getSettledAmount());
            long units = item.getUnitPrice() <= 0 ? 0 : Math.min(item.getSettledQuantity(), allocated / item.getUnitPrice());
            creator += item.getUnitReward() * units;
            refund += allocated;
            remaining -= allocated;
            first = first == null ? item : first;
        }
        if (first == null) {
            return null;
        }
        return new Target(first.getSettlementId(), first.getOrderId(), first.getDeliveryGroupId(),
                first.getOrderProductId(), refund, creator);
    }

    // ------------------------------------------------------------------ 2-5 · 6-2 반영

    @Override
    @Transactional(readOnly = true)
    public Pending pendingFor(Long marketId, Long creatorId) {
        long brand = clawbackRepository.findPendingBrand(marketId).stream().mapToLong(SettlementClawback::getAmount).sum();
        long reward = clawbackRepository.findPendingCreator(creatorId).stream().mapToLong(SettlementClawback::getAmount)
                .sum();
        return brand == 0 && reward == 0 ? Pending.NONE : new Pending(brand, reward);
    }

    /**
     * 생성 트랜잭션 안에서 — 측별로 오래된 행부터 반영 금액만큼 {@code APPLIED}. 다 못 뺀 행은 반영분으로 줄이고 남은 금액을 같은 번호
     * seq + 1 행으로 넘긴다. 손대지 못한 행은 PENDING 그대로다. 브랜드 측 반영 건마다 수정세금계산서 발행 대기 행.
     */
    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public void applyTo(Settlement settlement, SettlementAmounts amounts, LocalDateTime now) {
        List<Applied> applied = new ArrayList<>();
        applied.addAll(applySide(clawbackRepository.findPendingBrand(settlement.getMarketId()),
                amounts.brandClawbackAmount(), settlement.getId(), now));
        applied.addAll(applySide(clawbackRepository.findPendingCreator(settlement.getCreatorId()),
                amounts.rewardClawbackAmount(), settlement.getId(), now));
        if (applied.isEmpty()) {
            return;
        }
        Map<String, List<Applied>> byNumber = applied.stream()
                .collect(Collectors.groupingBy(a -> a.row().getClawbackNumber(), LinkedHashMap::new, Collectors.toList()));
        List<String> details = new ArrayList<>();
        byNumber.forEach((number, rows) -> details.add("%s · %s · −%,d".formatted(number,
                reasonLabel(rows.get(0).row().getReason()), rows.stream().mapToLong(Applied::amount).sum())));
        historyRecorder.recordBySystem(settlement.getId(), SettlementEventType.CLAWBACK_APPLIED,
                String.join(" / ", details), now);
        for (Applied part : applied) {
            if (part.row().getSide() == ClawbackSide.BRAND) {
                createCredit(settlement, part.row(), now);
            }
        }
    }

    /** 반영한 차감 행과 반영 금액 — 조건부 UPDATE 뒤의 엔티티는 영속성 컨텍스트에서 옛 값이라 반영 금액을 따로 든다. */
    private record Applied(SettlementClawback row, long amount) {
    }

    private List<Applied> applySide(List<SettlementClawback> pending, long appliedTotal, Long settlementId,
                                    LocalDateTime now) {
        List<Applied> applied = new ArrayList<>();
        long remaining = appliedTotal;
        for (SettlementClawback row : pending) {
            if (remaining <= 0) {
                break;
            }
            long take = Math.min(remaining, row.getAmount());
            if (clawbackRepository.apply(row.getId(), take, settlementId, now) != 1) {
                throw new BusinessException(ErrorCode.SETTLEMENT_STATE_CHANGED);
            }
            if (take < row.getAmount()) {
                clawbackRepository.save(row.carryOver(row.getAmount() - take, nextSeq(row), now));
            }
            remaining -= take;
            applied.add(new Applied(row, take));
        }
        return applied;
    }

    /**
     * 수정세금계산서(6-4) — 환불된 리워드분의 마이너스 발행 대기. 원 정산의 리워드 부가세율로 이 차감의 리워드분(인플루언서 측 행 합 ·
     * 이월 포함)과 부가세를 음수로 둔다. 리워드분이 없으면(리워드 0) 만들지 않는다.
     */
    private void createCredit(Settlement settlement, SettlementClawback brandRow, LocalDateTime now) {
        long reward = clawbackRepository.sumAmount(brandRow.getClawbackNumber(), ClawbackSide.CREATOR);
        if (reward <= 0) {
            return;
        }
        Settlement origin = settlementRepository.findById(brandRow.getOriginSettlementId()).orElse(settlement);
        long vat = BigDecimal.valueOf(reward).multiply(origin.getRewardVatRate()).setScale(0, RoundingMode.DOWN)
                .longValue();
        SettlementTaxDocument credit = SettlementTaxDocument.create(settlement.getId(),
                TaxDocumentType.BRAND_TAX_INVOICE_CREDIT, TaxDocumentStatus.PENDING_ISSUE, -reward, -vat,
                settlement.getMarket().getMarketName(), null, null, null, now);
        documentRepository.save(credit.withClawback(brandRow.getId()));
    }

    /**
     * 합의로 리워드가 이미 반영된 인플루언서 측 차감보다 작아졌다(4-3) — 넘친 만큼을 이 정산에 반영된 행에서 줄이고(최근 행부터) 같은 번호
     * seq + 1 PENDING 행으로 넘긴다.
     */
    @Transactional(propagation = Propagation.MANDATORY)
    public void carryOverAfterAgreement(Long settlementId, long carryover, LocalDateTime now) {
        long remaining = carryover;
        List<SettlementClawback> applied = new ArrayList<>(clawbackRepository.findByAppliedSettlementIdOrderByIdAsc(
                settlementId).stream().filter(c -> c.getSide() == ClawbackSide.CREATOR).toList());
        applied.sort(Comparator.comparing(SettlementClawback::getId).reversed());
        for (SettlementClawback row : applied) {
            if (remaining <= 0) {
                break;
            }
            long back = Math.min(remaining, row.getAmount());
            clawbackRepository.reduceApplied(row.getId(), row.getAmount() - back);
            clawbackRepository.save(row.carryOver(back, nextSeq(row), now));
            remaining -= back;
        }
    }

    /** 이월 행의 seq — 같은 번호 · 측의 마지막 seq + 1(이미 이월 중인 행이 있어도 겹치지 않는다). */
    private int nextSeq(SettlementClawback row) {
        return clawbackRepository.maxSeq(row.getClawbackNumber(), row.getSide()) + 1;
    }

    // ------------------------------------------------------------------ 3-6 미회수

    /** 대상 측이 다음 정산을 가질 수 없다 — 인플루언서 탈퇴 · 마켓 탈퇴. 표시만 한다(회수 방법은 [자문대기-법률 J]). */
    @Transactional
    public int markUnrecoverable(LocalDateTime now) {
        int marked = 0;
        for (SettlementClawback row : clawbackRepository.findPendingOfWithdrawnCreators()) {
            marked += mark(row, ClawbackUnrecoverableReason.CREATOR_WITHDRAWN, now);
        }
        for (SettlementClawback row : clawbackRepository.findPendingOfWithdrawnMarkets()) {
            marked += mark(row, ClawbackUnrecoverableReason.MARKET_WITHDRAWN, now);
        }
        return marked;
    }

    private int mark(SettlementClawback row, ClawbackUnrecoverableReason reason, LocalDateTime now) {
        if (clawbackRepository.markUnrecoverable(row.getId(), reason, now) != 1) {
            return 0;
        }
        historyRecorder.recordBySystem(row.getOriginSettlementId(), SettlementEventType.CLAWBACK_UNRECOVERABLE,
                "%s · %s 측 −%,d · %s".formatted(row.getClawbackNumber(), row.getSide().getLabel(), row.getAmount(),
                        reason.getLabel()), now);
        return 1;
    }

    // ------------------------------------------------------------------ 06a ④ · 06c

    @Override
    @Transactional(readOnly = true)
    public List<ClawbackView> findByDeliveryGroup(Long deliveryGroupId) {
        return clawbackRepository.findByDeliveryGroupIdOrderByIdAsc(deliveryGroupId).stream()
                .map(SettlementClawbackService::view).toList();
    }

    @Override
    @Transactional(readOnly = true)
    public List<ClawbackView> findByRefundTask(Long refundTaskId) {
        return clawbackRepository.findByRefundTaskIdOrderByIdAsc(refundTaskId).stream()
                .map(SettlementClawbackService::view).toList();
    }

    private static ClawbackView view(SettlementClawback c) {
        ClawbackStatus status = c.getStatus();
        return new ClawbackView(c.getClawbackNumber(), c.getSide().name(), status.name(), status.getLabel(),
                c.getAmount());
    }
}
