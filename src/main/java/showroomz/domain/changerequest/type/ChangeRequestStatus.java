package showroomz.domain.changerequest.type;

public enum ChangeRequestStatus {
    PENDING,
    APPROVED,
    REJECTED,
    /** 브랜드가 검토 중 철회. '전체' 탭에서만 노출된다(§15-9 확정 2). */
    CANCELED
}
