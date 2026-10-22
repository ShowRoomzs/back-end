package showroomz.domain.order.type;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * 취소 요청 거부 사유(1009 기획 수정본 3-3 · 파트너 10d E7 드롭다운) — 소비자에게 라벨과 상세가 그대로 전달된다(약관 제18조①).
 * {@link #ETC}만 상세 입력이 필수다.
 *
 * <p>1021 결정 노트 기-33 — 「주문 제작·맞춤 상품」은 거부 사유에서 뺐다(3종). 이미 저장된 행이 있어 값은 남기고
 * {@link #isSelectable() 선택 불가}로만 둔다 — 새 거부 요청에 들어오면 400.
 */
@Getter
@RequiredArgsConstructor
public enum CancelRejectReason {
    ALREADY_PACKED("이미 포장·출고가 완료됨", true),
    PICKED_UP("택배사 집화가 완료됨", true),
    /** @deprecated 기-33 삭제 — 기존 저장 행 표시용. 새 요청은 받지 않는다. */
    @Deprecated
    MADE_TO_ORDER("주문 제작·맞춤 상품", false),
    ETC("기타", true);

    private final String label;
    private final boolean selectable;
}
