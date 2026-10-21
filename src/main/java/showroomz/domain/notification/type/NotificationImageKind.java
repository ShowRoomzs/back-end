package showroomz.domain.notification.type;

/** C11 앞 이미지 3종 — 주문 · 배송 · 반품은 상품 썸네일, 쇼룸 소식은 아바타, 계정 · 서비스는 회색 아이콘. */
public enum NotificationImageKind {
    THUMB,
    /** image_ref = creator_id — 읽는 시점에 현재 프로필 이미지 · 진행 중 공구 로즈 링을 붙인다. */
    AVATAR,
    ICON
}
