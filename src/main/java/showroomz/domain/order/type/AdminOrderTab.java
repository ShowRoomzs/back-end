package showroomz.domain.order.type;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * 어드민 주문 조회 탭(06a A1 · A3) — 운영자가 여는 축 셋. 결제완료 · 상품준비중 · 배송중 · 배송완료 · 구매확정은 탭이 아니라 상태
 * 셀렉트다(DS §6 「탭은 4~5개까지」). 반품 환불은 반품·교환(06b), 환불 실패는 환불 관리(06c) 소관이라 여기는 「취소」만 남는다.
 */
@Getter
@RequiredArgsConstructor
public enum AdminOrderTab {
    ALL("전체"),
    /** 집화 확인 필요 · 추적 정지 · 반송 중. */
    DELIVERY_ISSUE("배송 이상"),
    /** 취소된 하위주문 · 검토 중 취소 요청. */
    CANCEL("취소");

    private final String label;
}
