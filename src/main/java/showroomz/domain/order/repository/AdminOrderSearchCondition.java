package showroomz.domain.order.repository;

import showroomz.domain.order.type.AdminOrderTab;
import showroomz.domain.order.type.FulfillmentStatus;

import java.time.LocalDateTime;

/**
 * 어드민 주문 조회 조건(06a) — 단위는 주문(결제 1건)이다. 하위주문 하나라도 조건에 맞으면 그 주문이 나온다.
 *
 * @param status  상태 셀렉트 — null 이면 전체
 * @param marketId 브랜드 상세에서 「이 브랜드 진행 주문 보기」로 넘어온 경우
 * @param keyword  주문번호 · 하위주문번호 · 수취인 · 브랜드명 · 송장번호
 */
public record AdminOrderSearchCondition(AdminOrderTab tab, FulfillmentStatus status, Long marketId, String keyword,
                                        LocalDateTime from, LocalDateTime to) {
}
