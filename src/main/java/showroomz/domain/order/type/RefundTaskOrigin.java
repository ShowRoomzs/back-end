package showroomz.domain.order.type;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * 환불 출처(1009 기획 수정본 2-2 · 어드민 06c 「출처」 열). 결제완료 취소 · 취소 요청 승인(자동 승인 포함) · 직권 취소 · 반품 검수
 * 통과 · 반송 완료는 PG 자동이고, 운영자 사유 환불(반려 이의 인용 · 구매확정 후 하자 · 위해성 리콜)만 운영자가 집행한다.
 */
@Getter
@RequiredArgsConstructor
public enum RefundTaskOrigin {
    PG_AUTO("PG 자동"),
    OPERATOR("운영자 사유");

    private final String label;
}
