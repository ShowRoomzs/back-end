package showroomz.domain.settlement.service.port;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import showroomz.domain.order.service.port.OrderSettlementReader;
import showroomz.domain.settlement.entity.Settlement;
import showroomz.domain.settlement.entity.SettlementItem;
import showroomz.domain.settlement.repository.SettlementItemRepository;
import showroomz.domain.settlement.repository.SettlementRepository;
import showroomz.domain.settlement.service.SettlementClawbackLedger;

import java.util.Comparator;
import java.util.List;
import java.util.Optional;

/**
 * 주문 → 정산 읽기 포트 구현(44 어드민 설계서 8-3). 명세 행({@code settlement_item})으로 「이 하위주문이 어느 정산에 얼마로
 * 들어갔는가」를 답한다. 정산 전이면 empty — 06a ④ 는 null, 06c 는 {@code BEFORE_SETTLEMENT}.
 */
@Component
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class SettlementOrderReader implements OrderSettlementReader {

    private final SettlementItemRepository itemRepository;
    private final SettlementRepository settlementRepository;
    private final SettlementClawbackLedger clawbackLedger;

    @Override
    public Optional<GroupSettlement> readByDeliveryGroup(Long deliveryGroupId) {
        List<SettlementItem> items = itemRepository.findByDeliveryGroupId(deliveryGroupId);
        if (items.isEmpty()) {
            return Optional.empty();
        }
        return settlementRepository.findById(items.get(0).getSettlementId()).map(settlement -> new GroupSettlement(
                settlement.getId(), settlement.getSettlementNumber(), settlement.getStatus().name(),
                settlement.getStatus().getLabel(), settlement.getConfirmedAt(),
                items.stream().mapToLong(SettlementItem::getSettledAmount).sum(),
                items.stream().mapToLong(SettlementItem::getRewardAmount).sum(),
                clawbackLedger.findByDeliveryGroup(deliveryGroupId).stream().map(SettlementOrderReader::ref).toList()));
    }

    @Override
    public Optional<RefundSettlement> readByRefundTask(Long refundTaskId, Long deliveryGroupId) {
        List<SettlementItem> items = itemRepository.findByDeliveryGroupId(deliveryGroupId);
        if (items.isEmpty()) {
            return Optional.empty();
        }
        Settlement settlement = settlementRepository.findById(items.get(0).getSettlementId()).orElse(null);
        if (settlement == null) {
            return Optional.empty();
        }
        // 한 환불 = 측별 2행. 06c 는 차감번호 하나로 보이므로 「덜 끝난 쪽」 상태를 대표로 쓴다(미회수 > 차감 예정 > 차감 반영).
        List<SettlementClawbackLedger.ClawbackView> views = clawbackLedger.findByRefundTask(refundTaskId);
        ClawbackRef clawback = views.stream()
                .min(Comparator.comparingInt(SettlementOrderReader::severity))
                .map(SettlementOrderReader::ref)
                .orElse(null);
        // 차감 행은 집행 뒤에 생긴다 — 그 전(M1 집행 재확인)에는 같은 식의 예정액을 싣는다(46 수정계획 5-1).
        SettlementClawbackLedger.ClawbackAmounts preview = views.isEmpty() ? clawbackLedger.preview(refundTaskId) : null;
        return Optional.of(new RefundSettlement(settlement.getId(), settlement.getSettlementNumber(), clawback,
                views.stream().map(SettlementOrderReader::ref).toList(),
                preview == null ? null : new ExpectedClawback(preview.brandAmount(), preview.creatorAmount())));
    }

    private static ClawbackRef ref(SettlementClawbackLedger.ClawbackView view) {
        return new ClawbackRef(view.clawbackNumber(), view.side(), view.status(), view.statusLabel(), view.amount());
    }

    private static int severity(SettlementClawbackLedger.ClawbackView view) {
        return switch (view.status()) {
            case "UNRECOVERABLE" -> 0;
            case "PENDING" -> 1;
            default -> 2;
        };
    }
}
