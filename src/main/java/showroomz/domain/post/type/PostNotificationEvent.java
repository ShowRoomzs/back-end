package showroomz.domain.post.type;

/**
 * 게시물 관련 통지 종류 (§24-5 "알리지 않고 사라지는 경우는 없다" · §24-6 "알림 이력에 영구 보존").
 *
 * <p>이력 적재와 발송은 분리돼 있다. {@code PostNotificationService}가 이력을 남기고,
 * {@code PostPushMessageFactory}가 종류별 문구를,
 * {@code PushPostNotificationSender}가 수신자(본인이냐 팔로워냐)를 정한다.
 */
public enum PostNotificationEvent {

    /** 운영자 노출 중지 — 사유·근거 규정·조치 시각·처리자·기한을 함께 통지한다 */
    SUSPENDED,

    /** 이의 신청 접수 */
    APPEAL_RECEIVED,

    /** 이의 신청 승인 → 재게시 */
    APPEAL_APPROVED,

    /** 이의 신청 반려 → 영구 삭제 + 원본 내려받기 유예 안내 */
    APPEAL_REJECTED,

    /** 기한 내 미신청으로 자동 삭제 */
    DELETED_BY_EXPIRE,

    /** 본인 삭제 — 되돌릴 수 없는 조작이라 이력에 남긴다 */
    DELETED_BY_SELF,

    /** 팔로워 신규 게시물 알림 — 수정 시에는 재발송하지 않는다 (§24-3) */
    PUBLISHED_TO_FOLLOWERS
}
