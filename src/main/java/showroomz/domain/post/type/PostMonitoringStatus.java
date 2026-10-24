package showroomz.domain.post.type;

/**
 * 어드민 모니터링 상태 (§54-1) — <b>저장하지 않는 파생값</b>이다(54 설계 0-2).
 *
 * <p>「신고 접수」는 조치 전 단계라 어드민에만 있다. 저장 상태({@link PostStatus})로 두면 신고가 들어올 때마다
 * 게시물 상태를 건드려야 하고, 그 순간 스튜디오 상태 탭이 어드민 사정으로 흔들린다. 그래서 대기 신고 수로 계산한다.
 * 신고 종결이 "게시중으로 되돌린다"는 것도 상태 전이가 아니라 대기 신고가 0이 되는 것이다.
 *
 * <p>색 — 게시중=성공 / 신고 접수 · 이의 신청 심사 중=경고 / 노출 중지=위험 / 영구 삭제=중립.
 */
public enum PostMonitoringStatus {
    /** 게시중 · 대기 신고 없음 */
    PUBLISHED("게시중"),
    /** 게시중 · 대기 신고 있음 — 운영자가 지금 판단할 상태 */
    REPORTED("신고 접수"),
    /** 노출 중지 · 이의 신청 전 — 기한이 지나면 영구 삭제 */
    SUSPENDED("노출 중지"),
    /** 이의 신청 심사 중 — 운영자가 지금 판단할 상태 */
    UNDER_REVIEW("이의 신청 심사 중"),
    /** 영구 삭제 · 비공개 보관 — 운영자 콘솔 전용 조회 */
    CLOSED("영구 삭제"),
    /** 작성중 — 노출된 적이 없어 목록 · 요약에는 나오지 않는다. 상세를 직접 열었을 때만 */
    DRAFT("작성중");

    private final String label;

    PostMonitoringStatus(String label) {
        this.label = label;
    }

    public String getLabel() {
        return label;
    }

    public static PostMonitoringStatus of(PostStatus status, long pendingReportCount) {
        return switch (status) {
            case PUBLISHED -> pendingReportCount > 0 ? REPORTED : PUBLISHED;
            case SUSPENDED -> SUSPENDED;
            case UNDER_REVIEW -> UNDER_REVIEW;
            case DELETED -> CLOSED;
            case DRAFT -> DRAFT;
        };
    }

    /** 조치 필요 — GNB 배지에 들어가는 상태 */
    public boolean requiresAction() {
        return this == REPORTED || this == UNDER_REVIEW;
    }
}
