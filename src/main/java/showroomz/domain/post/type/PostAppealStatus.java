package showroomz.domain.post.type;

/**
 * 이의 신청 심사 상태 (§24-5). 게시물당 1회이므로 재신청 상태는 없다.
 */
public enum PostAppealStatus {

    /** 심사 중 — 인플루언서 화면에는 액션 없이 상태와 예상 소요만 보인다 */
    PENDING,

    /** 승인 → 재게시 */
    APPROVED,

    /** 반려 → 영구 삭제 + 원본 내려받기 유예 시작 */
    REJECTED
}
