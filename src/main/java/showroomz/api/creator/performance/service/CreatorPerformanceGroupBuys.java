package showroomz.api.creator.performance.service;

import jakarta.persistence.EntityManager;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import showroomz.domain.groupbuy.entity.GroupBuy;
import showroomz.domain.groupbuy.type.GroupBuyStatus;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 스튜디오 성과 모집단(48 파트너 설계서 0-3 · 49 스튜디오 설계서 0-A) — <b>내</b> 종료 공구({@code ENDED · SETTLED}) ∧ 결제 완료
 * 하위주문 ≥ 1 ∧ 종료일이 조회 기간 안. 판정식은 공통 리더의 {@code isListed} 와 같다(목록에 보이는 공구 = 상세가 열리는 공구).
 *
 * <p>공구 리포지토리는 여러 화면이 함께 고치는 파일이라 스튜디오 전용 목록 조회는 여기 둔다. 계약 · 브랜드 · 인플루언서를 함께 올린다 —
 * 리더가 그 셋을 읽는다.
 */
@Component
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class CreatorPerformanceGroupBuys {

    /** 한 인플루언서의 기간 안 종료 공구 상한 — 수동 페이징 · KPI 가 메모리에서 돈다(12개월 기준 넉넉한 값). */
    static final int MAX_ROWS = 500;

    private final EntityManager entityManager;

    /** 종료일 최신순(같으면 id 역순). */
    public List<GroupBuy> findListed(Long creatorId, LocalDateTime endedSince) {
        return entityManager.createQuery("SELECT g FROM GroupBuy g "
                        + "JOIN FETCH g.contract c JOIN FETCH g.market m JOIN FETCH g.creator cr "
                        + "WHERE cr.id = :creatorId AND g.status IN :statuses "
                        + "AND COALESCE(g.endedAt, g.endAt) >= :since "
                        + "AND EXISTS (SELECT 1 FROM OrderDeliveryGroup d WHERE d.groupBuy = g "
                        + "    AND d.order.paidAt IS NOT NULL "
                        + "    AND d.fulfillmentStatus <> showroomz.domain.order.type.FulfillmentStatus.PENDING) "
                        + "ORDER BY COALESCE(g.endedAt, g.endAt) DESC, g.id DESC", GroupBuy.class)
                .setParameter("creatorId", creatorId)
                .setParameter("statuses", List.of(GroupBuyStatus.ENDED, GroupBuyStatus.SETTLED))
                .setParameter("since", endedSince)
                .setMaxResults(MAX_ROWS)
                .getResultList();
    }
}
