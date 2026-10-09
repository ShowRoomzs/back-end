package showroomz.domain.order.service.port;

import org.springframework.stereotype.Component;
import showroomz.domain.order.entity.OrderRefundTask;

/**
 * 차감 모듈(44 구현 계획서 단계 8) 전의 빈 구현 — 환불은 정산에 아무 영향이 없다. 정산이 {@link SettlementClawbackHook}을
 * 구현하면 이 클래스를 지운다.
 */
@Component
public class EmptySettlementClawbackHook implements SettlementClawbackHook {

    @Override
    public void onRefundExecuted(OrderRefundTask task) {
        // 차감 모듈 전 — 무시한다.
    }
}
