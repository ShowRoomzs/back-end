package showroomz.domain.groupbuy.service.port;

import java.time.LocalDateTime;

/**
 * 공구 종결 → 주문 모듈 훅. 공구 모듈이 주문을 직접 알지 않게 포트로 둔다(판매 포트 {@link GroupBuySalesReader}와 같은 방향).
 * 종결 트랜잭션 안에서 불린다.
 */
public interface GroupBuyClosureHook {

    /**
     * 공구가 종결됐다(기간 종료 · 조기 마감 · 중단 전부) — 그 공구 주문의 발송 기한을 확정한다(1009 기획 수정본 1-2).
     *
     * @param endedAt 실제 종결 시각 — 기간 종료는 {@code end_at}, 조기 마감 · 중단은 판정 시각
     */
    void onGroupBuyClosed(Long groupBuyId, LocalDateTime endedAt);
}
