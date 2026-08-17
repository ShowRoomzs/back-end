package showroomz.domain.post.type;

/**
 * 삭제가 어떤 경로로 일어났는지 (§24-5 세 갈래 + 운영자 직권).
 *
 * <p>상태({@link PostStatus#DELETED})만으로는 "왜 사라졌는가"가 남지 않는다. 보관 기간이 끝나
 * 게시물이 파기된 뒤에도 알림 이력에는 이 값이 문구로 굳어 남는다(§24-6).
 */
public enum PostDeleteReason {

    /** 본인 삭제 — 게시중이든 노출 중지 중이든 인플루언서가 직접 내렸다 */
    SELF,

    /** 이의 신청 반려 → 영구 삭제 */
    APPEAL_REJECTED,

    /** 이의 신청 기한 내 미신청 → 영구 삭제 */
    APPEAL_EXPIRED,

    /** 운영자 직권 삭제 */
    ADMIN
}
