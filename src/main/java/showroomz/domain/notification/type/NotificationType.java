package showroomz.domain.notification.type;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * 알림 유형 — 한 곳에서만 늘린다(1017 설계서 1-2 · 1-3 · 1-4 · 1-5). 유형이 화면 분류(category) · 광고성 여부를 든다.
 *
 * <p><b>광고성</b> 4종(팔로우 쇼룸 새 공구 · 새 게시물 · 공구 마감 임박 · 혜택)만 수신 동의가 있어야 행이 생기고, 푸시는 제목 앞
 * 「(광고)」 · 야간(21~08시) 미발송 · 새 게시물은 하루 묶음이다. 그 밖은 거래 · 서비스 알림이라 끌 수 없다.
 */
@Getter
@RequiredArgsConstructor
public enum NotificationType {

    // ── 소비자(C11) ─────────────────────────────────────────────────────────
    ORDER_SHIPPED("주문", false),
    ORDER_DELIVERED("주문", false),
    ORDER_CONFIRM_D1("주문", false),
    CANCEL_REQUEST_APPROVED("주문", false),
    CANCEL_REQUEST_REJECTED("주문", false),
    CLAIM_COLLECT_INVOICE_NEEDED("반품·교환", false),
    CLAIM_RESHIP_FEE_NEEDED("반품·교환", false),
    CLAIM_INSPECTION_PASSED("반품·교환", false),
    CLAIM_INSPECTION_REJECTED("반품·교환", false),
    /** 운임 환급 초과분 계좌 송금 완료(1021 설계서 4-5) — 「계좌로 N원을 송금했어요」. */
    CLAIM_REFUND_TRANSFERRED("반품·교환", false),
    REFUND_COMPLETED("주문", false),
    INQUIRY_ANSWERED("문의", false),
    FOLLOW_GROUP_BUY_OPENED("쇼룸", true),
    FOLLOW_POST_PUBLISHED("쇼룸", true),
    GROUP_BUY_ENDING_D1("쇼룸", true),
    /** 혜택 · 이벤트 — 발송 수단(어드민 공지 푸시)이 아직 없다. 유형만 예약(12절 Q4). */
    BENEFIT("혜택", true),
    USER_ACCOUNT_SUSPENDED("계정", false),
    USER_ACCOUNT_RELEASED("계정", false),
    MARKETING_CONSENT_CHANGED("계정", false),
    TERMS_CHANGED("서비스", false),

    // ── 파트너센터 · 쇼룸 스튜디오(16 알림 표 · 14 H1a) ─────────────────────────
    PRODUCT_UNLISTED_BY_ADMIN("상품", false),
    PRODUCT_RELISTED_BY_ADMIN("상품", false),

    CONNECTION_REQUESTED("연결", false),
    CONNECTION_ACCEPTED("연결·소통", false),
    CONNECTION_REJECTED("연결·소통", false),
    OPERATOR_CHANNEL_REPLY("연결·소통", false),

    CONTRACT_REVIEW_PASSED("계약", false),
    CONTRACT_REVIEW_REJECTED("계약", false),
    CONTRACT_SIGNATURE_REQUESTED("계약", false),
    CONTRACT_COUNTERPARTY_SIGNED("계약", false),
    CONTRACT_DECLINED("계약", false),
    CONTRACT_CONCLUDED("계약", false),
    CONTRACT_EXPIRED("계약", false),
    CONTRACT_CANCELED_BY_ADMIN("계약", false),
    CONTRACT_RESEND_HANDLED("계약", false),
    CONTRACT_SIGN_DEADLINE_D1("계약", false),
    CONTRACT_FIXED_FEE_PAID("계약", false),
    /** 양측 서명 완료 · 계약 성립(1021 기-05). */
    CONTRACT_ESTABLISHED("계약", false),
    /** 상대가 해지를 요청했다(1021 설계서 1-4) — 의견은 운영팀 채널. */
    CONTRACT_TERMINATION_REQUESTED("계약", false),
    /** 해지 처리됨 — 양측. */
    CONTRACT_TERMINATED("계약", false),

    GROUP_BUY_POST_SUBMITTED("공구", false),
    GROUP_BUY_POST_APPROVED("공구", false),
    GROUP_BUY_POST_REJECTED("공구", false),
    GROUP_BUY_POST_HIDDEN("공구", false),
    GROUP_BUY_POST_UNHIDDEN("공구", false),
    GROUP_BUY_EXTENSION_REQUESTED("공구", false),
    GROUP_BUY_EXTENSION_ACCEPTED("공구", false),
    GROUP_BUY_EXTENSION_REJECTED("공구", false),
    GROUP_BUY_EXTENSION_EXPIRED("공구", false),
    GROUP_BUY_SUSPENSION_REQUESTED("공구", false),
    GROUP_BUY_EARLY_CLOSE_REQUESTED("공구", false),
    GROUP_BUY_REQUEST_APPROVED("공구", false),
    GROUP_BUY_REQUEST_REJECTED("공구", false),
    GROUP_BUY_ADMIN_SUSPENSION_NOTICED("공구", false),
    GROUP_BUY_ADMIN_SUSPENSION_WITHDRAWN("공구", false),
    GROUP_BUY_SUSPENDED_BY_ADMIN("공구", false),
    GROUP_BUY_OPENED("공구", false),
    GROUP_BUY_ENDED("공구", false),

    ORDER_DELIVERED_BY_ADMIN("판매", false),
    ORDER_INVOICE_BY_ADMIN("판매", false),
    ORDER_OPERATOR_REFUND("판매", false),
    CLAIM_OPENED_BY_ADMIN("판매", false),
    /** 추적 정지 소비자 선택(1021 기-15) — 「소비자가 재발송/환불을 선택했어요」. */
    ORDER_STALL_CHOICE_MADE("판매", false),
    CANCEL_AUTO_APPROVED("판매", false),
    RETURN_DETECTED("판매", false),
    TRACKING_STALLED("판매", false),

    SETTLEMENT_CREATED("정산", false),
    /** 확인 기간 중 환불 재계산(1021 기-13) — 금액이 바뀌고 확인 기간이 다시 시작됐다. */
    SETTLEMENT_RECALCULATED("정산", false),
    SETTLEMENT_CONFIRMED("정산", false),
    SETTLEMENT_PAID("정산", false),
    REWARD_PAID("정산", false),
    CLAWBACK_APPLIED("정산", false),
    TAX_INVOICE_NEEDED("정산", false),
    TAX_INVOICE_VERIFIED("정산", false),
    TAX_INVOICE_REJECTED("정산", false),
    ADJUSTMENT_REQUESTED("정산", false),
    ADJUSTMENT_COUNTERED("정산", false),
    ADJUSTMENT_REJECTED("정산", false),
    ADJUSTMENT_AGREED("정산", false),
    ADJUSTMENT_DEADLINE_D1("정산", false),
    ADJUSTMENT_EXPIRED("정산", false),
    /** 상대가 조정 요청을 철회했다(1021 기-14). */
    ADJUSTMENT_WITHDRAWN("정산", false),

    INQUIRY_DELETE_APPROVED("문의", false),
    INQUIRY_DELETE_REJECTED("문의", false),

    CHANGE_REQUEST_APPROVED("기본정보", false),
    CHANGE_REQUEST_REJECTED("기본정보", false),
    WITHDRAWAL_APPROVED("기본정보", false),
    WITHDRAWAL_REJECTED("기본정보", false),
    ACCOUNT_VERIFICATION_FAILED("기본정보", false),
    BUSINESS_TYPE_CHANGE_APPROVED("기본정보", false),
    BUSINESS_TYPE_CHANGE_REJECTED("기본정보", false),
    /** 정산 계좌 변경 요청 승인 · 반려(1021 기-22). */
    ACCOUNT_CHANGE_APPROVED("기본정보", false),
    ACCOUNT_CHANGE_REJECTED("기본정보", false),

    ACCOUNT_SUSPENDED("계정", false),
    ACCOUNT_RELEASED("계정", false),
    /** 상대 정지로 체결 · 오픈이 보류 · 재개(8-7) — 정지 사실 · 사유는 문장에 넣지 않는다. */
    COUNTERPARTY_HOLD_STARTED("계약", false),
    COUNTERPARTY_HOLD_RESUMED("계약", false);

    /** 화면 분류 태그 — 스튜디오 「계약 · 공구 · 연결 · 정산」 · 소비자 아이콘 분기. */
    private final String category;
    /** 광고성 — 수신 동의 필요 · (광고) 표기 · 야간 미발송. */
    private final boolean advertising;
}
