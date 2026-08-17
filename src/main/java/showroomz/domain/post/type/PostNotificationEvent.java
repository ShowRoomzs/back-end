package showroomz.domain.post.type;

/**
 * 게시물 관련 통지 종류 (§24-5 "알리지 않고 사라지는 경우는 없다" · §24-6 "알림 이력에 영구 보존").
 *
 * <p>발송 인프라는 이 프로젝트에 아직 없다({@code NotificationSetting}은 수신 설정값일 뿐이다).
 * 그래서 <b>이력은 지금 남기고 발송 어댑터는 no-op 스텁</b>으로 둔다 — 이력은 소급 생성이
 * 불가능하지만 발송은 인프라가 생긴 뒤 붙이면 되기 때문이다.
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
