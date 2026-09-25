package showroomz.domain.groupbuy.type;

/**
 * 공구 이력 이벤트 — 시안 이력 문구와 1:1(설계서 1-4).
 *
 * <p><b>만들지 않는 값 — {@code FIXED_FEE_PAID}.</b> §29-9 「시스템은 지급 사실을 알지 못한다」.
 * 고정 지급비 자기 신고는 계약 관리 소관이다.
 */
public enum GroupBuyEventType {
    /** 공구 생성 · 계약 조건 상속 — SYSTEM(설계서 7-1 #7). */
    CREATED,
    /** 최소 준비 물량 확보 확인 — 제25조 제재 판정의 증거. */
    STOCK_CONFIRMED,
    /** 인플루언서 게시물 등록 · 오픈 승인 요청. */
    POST_SUBMITTED,
    /** 운영자 오픈 승인 · 준비완료. */
    OPEN_APPROVED,
    OPEN_REJECTED,
    /**
     * 준비 게이트 충족 · 준비완료 — 운영자 승인보다 브랜드 확인이 나중일 때의 전이 기록.
     * 운영자가 마지막이면 {@link #OPEN_APPROVED}가 준비완료를 함께 말한다(설계서 3-2).
     */
    READY,
    /** 공구 시작 · 게시물 노출 시작 — SYSTEM. */
    OPENED,
    POST_HIDDEN,
    POST_UNHIDDEN,
    EXTENSION_REQUESTED,
    EXTENSION_ACCEPTED,
    EXTENSION_REJECTED,
    /** 연장 요청 기간 만료 자동 거절 — SYSTEM. */
    EXTENSION_EXPIRED,
    EARLY_CLOSE_REQUESTED,
    EARLY_CLOSE_REJECTED,
    EARLY_CLOSED,
    SUSPENSION_REQUESTED,
    SUSPENSION_REJECTED,
    SUSPENDED,
    SUSPENSION_NOTICED,
    APPEAL_SUBMITTED,
    SUSPENSION_WITHDRAWN,
    SUSPENDED_BY_ADMIN,
    SUSPENDED_EMERGENCY,
    /** 공구 종료 · 게시물 노출 종료 — SYSTEM. */
    ENDED,
    ISSUE_OPENED,
    FULFILLMENT_CONFIRMED,
    FULFILLMENT_DISPUTED,
    FULFILLMENT_AUTO_CONFIRMED,
    /** 이행 3자 스레드 양측 동의 종결 — SYSTEM(연결·소통 통보). 보류 해제와 다른 사건이다(32 설계 1-4 ②). */
    FULFILLMENT_AGREED,
    /** 정산 보류 해제 — 정산 관리가 합의 이후에 푼다(32 설계 8-4). */
    FULFILLMENT_RESOLVED,
    SALES_FINALIZED,
    SETTLED
}
