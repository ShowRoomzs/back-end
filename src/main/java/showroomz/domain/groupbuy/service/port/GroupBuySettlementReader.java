package showroomz.domain.groupbuy.service.port;

import java.util.Optional;

/**
 * 정산 포트 — 정산 모듈이 확정한 리워드를 <b>그대로</b> 받는다(30 설계 5-2 · 31 설계 4-5). 공구는 정산 명세를 계산하지 않는다.
 *
 * <p>정산 모듈이 생기기 전에는 {@link EmptyGroupBuySettlementReader}가 empty를 돌려주고 응답은 null이 된다 — 0이 아니다.
 */
public interface GroupBuySettlementReader {

    /** 정산완료 공구의 확정 리워드(공제 전). */
    Optional<Long> readConfirmedReward(Long groupBuyId);
}
