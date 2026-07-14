package showroomz.domain.notification.type;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * 인앱 알림 발생 지점 (거래 플로우 §16 #1~16).
 * 베타는 인앱 전용 — 예외: 입점 승인/반려(#13)는 이메일/문자 발송(활성 전 단계라 인앱 수신 불가).
 */
@Getter
@RequiredArgsConstructor
public enum NotificationEventType {
    CONNECTION_REQUESTED("연결 요청 도착"),            // #1 → 인플루언서
    CONNECTION_ACCEPTED("연결 수락됨"),                // #2 → 브랜드
    CONTRACT_SIGN_REQUESTED("계약 서명 요청"),         // #3 → 인플루언서 → (서명 후) 브랜드
    CONTRACT_COMPLETED("계약 체결 완료"),              // #4 → 브랜드·인플루언서
    THREAD_NEW_MESSAGE("소통 스레드 새 글"),           // #5 → 상대방
    GROUP_BUY_APPROVAL_RESULT("공구 오픈 승인/반려"),  // #6 → 인플루언서·브랜드
    NEW_ORDER("새 주문 발생"),                         // #7 → 브랜드
    DELIVERY_STATUS_CHANGED("배송 상태 변경"),         // #8 → 소비자
    RETURN_STATUS_CHANGED("반품 요청/승인/완료"),      // #9 → 브랜드·소비자
    CS_INQUIRY_RECEIVED("CS 문의 도착"),               // #10 → 브랜드(상품)·운영자(그 외)
    CS_ANSWER_REGISTERED("CS 답변 등록"),              // #11 → 소비자
    SETTLEMENT_COMPLETED("정산 완료(이체)"),           // #12 → 브랜드·인플루언서
    ONBOARDING_RESULT("입점 승인/반려"),               // #13 — 이메일/문자 발송 예외
    CREATOR_POST_PUBLISHED("인플루언서 게시물 등록"),  // #14 → 팔로워 팬아웃
    ADMIN_ENFORCEMENT("운영자 집행 통지"),             // #15 → 당사자(숨김·미진열·공구 취소, 사유 포함)
    CONTRACT_RECALLED("계약 발송 취소(회수)");         // #16 → 인플루언서

    private final String description;
}
