package showroomz.domain.post.type;

/**
 * 노출 중지 조치가 어떻게 끝났는지 (§24-5).
 *
 * <p>{@code null}인 행이 <b>진행 중인 조치</b>다 — 게시물당 조치는 여러 번 있을 수 있으므로
 * (재게시 후 재조치) 현재 진행 중인 건을 이 값으로 가린다.
 */
public enum SuspensionResolution {

    /** 이의 신청 승인 → 재게시. 좋아요·인사이트는 그대로 복원된다 */
    REPUBLISHED,

    /** 이의 신청 반려 → 영구 삭제 */
    DELETED_BY_REJECT,

    /** 기한 내 미신청 → 영구 삭제 */
    DELETED_BY_EXPIRE,

    /** 중지 기간 중 본인 삭제 — 다투지 않고 나가는 출구다 (§24-5) */
    DELETED_BY_SELF
}
