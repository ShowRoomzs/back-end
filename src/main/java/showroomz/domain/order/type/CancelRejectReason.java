package showroomz.domain.order.type;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * 취소 요청 거부 사유(1009 기획 수정본 3-3 · 파트너 10d E7 드롭다운) — 소비자에게 라벨과 상세가 그대로 전달된다(약관 제18조①).
 * {@link #ETC}만 상세 입력이 필수다.
 */
@Getter
@RequiredArgsConstructor
public enum CancelRejectReason {
    ALREADY_PACKED("이미 포장·출고가 완료됨"),
    PICKED_UP("택배사 집화가 완료됨"),
    MADE_TO_ORDER("주문 제작·맞춤 상품"),
    ETC("기타");

    private final String label;
}
