package showroomz.domain.groupbuy.type;

/**
 * 목록 비고 열 — 상태만으로 알 수 없는 예외만 적는다(§30-1 · 설계서 4-2).
 *
 * <p><b>선언 순서가 우선순위다.</b> 동시에 둘이 걸리는 조합(연장 대기 + 중단 요청 검토)이 있어 하나만 내리고,
 * 판매가 끊길 가능성이 큰 쪽이 위다. 문구는 FE가 코드로 고른다 — 서버가 표시 문자열을 지으면
 * 3서피스 호칭 차이를 서버가 떠안는다.
 */
public enum GroupBuyRemarkCode {
    ADMIN_SUSPENSION_NOTICED,
    SUSPENSION_REQUEST_REVIEWING,
    EARLY_CLOSE_REQUEST_REVIEWING,
    EXTENSION_PENDING,
    SUSPENDED_BY_ADMIN
}
