package showroomz.domain.order.type;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * 소비자 앱 주문 항목의 「표시 상태」(C10 설계서 1-1) — <b>저장하지 않고 조회 때 유도</b>한다(0-3).
 * 이행(그룹)·취소 사실(항목)·취소 요청·반품교환 클레임 네 축의 합성값이라 컬럼으로 두면 동기화가 필요해진다.
 * 유도 규칙은 {@code UserOrderItemAssembler} 한 곳에만 있다.
 *
 * <p>{@code RETURNED} · {@code RETURN_IN_PROGRESS} · {@code EXCHANGE_IN_PROGRESS}는 반품·교환 클레임(35 설계서)이 원천이다 —
 * 클레임 테이블이 생기는 배포(C10 설계서 P5)에서 유도가 켜진다. 값은 응답 계약이라 먼저 둔다.
 */
@Getter
@RequiredArgsConstructor
public enum UserOrderItemStatus {

    CANCELLED("취소", UserOrderTone.MUTED, true),
    RETURNED("반품", UserOrderTone.MUTED, true),
    /** 탈색이 기본이다 — 검수 반려 단계(반려된 상품이 돌아오는 중)만 조립기가 탈색을 푼다(C10 설계서 1-1). */
    RETURN_IN_PROGRESS("반품", UserOrderTone.MUTED, true),
    EXCHANGE_IN_PROGRESS("교환", UserOrderTone.MUTED, true),
    CANCEL_REQUESTED("취소 요청중", UserOrderTone.MUTED, false),
    PAID("결제완료", UserOrderTone.ACTIVE, false),
    PREPARING("상품준비중", UserOrderTone.MUTED, false),
    SHIPPING("배송중", UserOrderTone.ACTIVE, false),
    /** 택배 반송(배송 실패) — 소비자가 신청하는 반품과 다른 사건이다. 시안에 없어 라벨만 둔다(미결 U3). */
    RETURNING("반송중", UserOrderTone.MUTED, false),
    DELIVERED("배송완료", UserOrderTone.ACTIVE, false),
    CONFIRMED("구매확정", UserOrderTone.MUTED, false),
    /** 결제 전 — 목록은 결제된 주문만이라 상세에서만 도달한다. */
    PAYMENT_PENDING("결제 대기", UserOrderTone.MUTED, false);

    private final String label;
    private final UserOrderTone tone;
    /** 행 탈색 — 끝난 항목(취소·반품)과 반품·교환 진행 중. */
    private final boolean dimmed;
}
