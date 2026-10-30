package showroomz.domain.notification.type;

/**
 * 알림을 눌렀을 때 갈 곳(1017 설계서 1-0 ④) — 목록이 아니라 그 건의 상세다. 상태를 재현하지 않는다(지금 상태의 상세로 간다).
 * 대상이 지워졌으면 FE 가 상세 404 를 받아 토스트 「이미 삭제된 항목입니다」로 처리한다.
 */
public enum NotificationTargetType {
    /** 하위주문(주문 상세 모달 · 앱 주문 상세) — id = order_delivery_group */
    ORDER_GROUP,
    /** 반품 · 교환 클레임 — id = order_claim */
    CLAIM,
    /** 1:1 문의 · 상품 문의 — id = 문의 id · sub = ONE_TO_ONE | PRODUCT */
    INQUIRY,
    POST,
    /** 쇼룸 — id = creator_id */
    SHOWROOM,
    GROUP_BUY,
    CONTRACT,
    SETTLEMENT,
    /** 정산 조정(이슈 스레드의 요청 카드) — id = settlement_id */
    ADJUSTMENT,
    /** 스레드 · 메시지 — id = thread_id · sub = message id(카드 위치) */
    THREAD,
    PRODUCT,
    /** 기본정보 관리 탭 — sub = 탭 키(소문자 · FE 탭 키 · account · settlement · 변경 요청 유형) */
    BASIC_INFO_TAB,
    /** 연결 요청함 */
    CONNECTION_REQUESTS,
    TERMS,
    /** 앱 설정 · 알림 설정 */
    SETTINGS,
    /** 앱 계정 상태(정지 안내 시트) */
    ACCOUNT_STATUS
}
