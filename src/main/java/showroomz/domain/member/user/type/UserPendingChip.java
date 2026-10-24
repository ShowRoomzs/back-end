package showroomz.domain.member.user.type;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * 소비자 상세 레일 「진행 중」 칩 5종(ui-admin-13 CS 응대 보강 ② · 55 계획서 M3-2) — 「응대 전 확인」. 1건 이상인 것만 칩으로 내리고, 나머지는
 * FE 가 「그 외 0건」 한 줄로 접는다. 열린 문의만 경고색이다. 순서는 시안 칩 순서(열린 문의 먼저)다. {@code BrandPendingChip}과 같은 방식.
 *
 * <p>칩 건수 = 칩이 가리키는 목록을 그 탭 · 상태 + {@code userId}로 부른 결과 건수다(56 C1). 목록 · 쿼리는 응답의 {@code links}가 싣는다.
 */
@Getter
@RequiredArgsConstructor
public enum UserPendingChip {
    /** 답변 전 1:1 문의(1:1 문의 관리 status=WAITING) + 상품 문의(상품 문의 모니터링 status=WAITING — 답변 대기 ∧ 정상 노출). */
    OPEN_INQUIRY("열린 문의", "WARNING", "INQUIRIES"),
    /** 배송 중인 하위주문이 있는 주문 수 — 주문 목록 status=SHIPPING&userId= 와 같은 수. */
    SHIPPING("배송중", "NEUTRAL", "ORDERS"),
    /** 브랜드 검토 중인 취소 요청이 있는 주문 수 — 주문 목록 tab=CANCEL&cancelRequested=true&userId= 와 같은 수. */
    CANCEL_REQUESTED("취소 요청", "NEUTRAL", "ORDERS"),
    /**
     * 끝나지 않은 반품 · 교환 클레임(목록 행) 수 — 클레임 목록 tab=ALL&openOnly=true&userId=(기간 없음)와 같은 수. 탈퇴 차단
     * ({@code OrderClaimRepository.countOpenCollectionsByUser})은 요청(박스) 단위라 숫자는 다를 수 있고 0 여부만 같다.
     */
    RETURN_EXCHANGE("반품·교환", "NEUTRAL", "CLAIMS"),
    /** PG 가 거절했거나 결과를 확인하지 못한 환불 — 어드민 환불 관리 「실패」 탭(tab=FAILED&userId=). */
    REFUND_FAILED("환불 실패", "NEUTRAL", "REFUNDS");

    private final String label;
    private final String tone;
    /** 이동할 어드민 화면 — FE 가 userId 를 붙여 라우팅한다. 정확한 목록 · 쿼리는 응답의 {@code links}. */
    private final String target;
}
