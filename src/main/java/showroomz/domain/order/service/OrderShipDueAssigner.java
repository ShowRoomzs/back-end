package showroomz.domain.order.service;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import showroomz.domain.groupbuy.service.port.GroupBuyClosureHook;
import showroomz.domain.order.repository.OrderDeliveryGroupRepository;

import java.time.LocalDateTime;

/**
 * 공구 종결 순간 그 공구 주문의 발송 기한을 일괄 확정한다(1009 기획 수정본 1-2) — 진행 중 결제된 주문은 마감 전까지 기한이
 * 없었다. N 은 주문마다 스냅샷이 달라 N 값별로 한 번씩 UPDATE 한다(최대 7종).
 *
 * <p>대상은 아직 기한이 없는 결제 대기 · 신규 · 상품준비중이다. 결제 대기까지 넣는 이유 — 종결과 결제 확정이 동시에 커밋되면
 * 결제 쪽이 종결 전 상태를 읽어 기한을 비워 둘 수 있다. 여기서 먼저 채워 두면 결제 확정의 {@code COALESCE}가 그대로 둔다.
 */
@Component
@RequiredArgsConstructor
public class OrderShipDueAssigner implements GroupBuyClosureHook {

    private final OrderDeliveryGroupRepository deliveryGroupRepository;
    private final ShipDuePolicy shipDuePolicy;

    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public void onGroupBuyClosed(Long groupBuyId, LocalDateTime endedAt) {
        if (groupBuyId == null || endedAt == null) {
            return;
        }
        for (Integer businessDays : deliveryGroupRepository.findShipDueBusinessDaysAwaitingDue(groupBuyId)) {
            deliveryGroupRepository.assignShipDue(groupBuyId, businessDays, shipDuePolicy.dueAt(endedAt, businessDays));
        }
    }
}
