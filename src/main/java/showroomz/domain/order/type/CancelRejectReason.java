package showroomz.domain.order.type;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * 취소 요청 거부 사유(1009 기획 수정본 3-3 · 파트너 10d E7 드롭다운) — 소비자에게 라벨과 상세가 그대로 전달된다(약관 제18조①).
 * {@link #ETC}만 상세 입력이 필수다.
 *
 * <p>1021 결정 노트 기-33 — 「주문 제작·맞춤 상품」(MADE_TO_ORDER)은 거부 사유에서 뺐다(3종). 저장된 행은 V221 이 「기타」로
 * 옮겼다(상세가 비면 원래 라벨) — 그 값으로 오는 요청은 역직렬화에서 400 이다.
 */
@Getter
@RequiredArgsConstructor
public enum CancelRejectReason {
    ALREADY_PACKED("이미 포장·출고가 완료됨"),
    PICKED_UP("택배사 집화가 완료됨"),
    ETC("기타");

    private final String label;
}
