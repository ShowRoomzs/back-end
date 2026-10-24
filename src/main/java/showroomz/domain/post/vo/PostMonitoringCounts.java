package showroomz.domain.post.vo;

/** 어드민 일반 게시물 탭 건수 (§54-2) — 한 쿼리에서 함께 센다 */
public record PostMonitoringCounts(long all, long reported, long suspended, long underReview, long closed) {

    /** GNB 배지 — 조치 필요 총건수 = 신고 접수 + 이의 신청 심사 중(C9: 신고 탭이 비어도 심사 1건이면 1) */
    public long actionRequired() {
        return reported + underReview;
    }
}
