package showroomz.domain.post.type;

/**
 * 소비자 신고 사유 코드 (C18 신고 시트 · §54 rev.3 확정 6종).
 *
 * <p>값을 {@link PostSuspensionReason}의 <b>부분집합</b>으로 맞춘다. 신고는 조치의 입구이고, 운영자가
 * 신고를 받아 노출 중지를 누르는 순간 사유 코드가 그대로 이어져야 "무엇을 이유로 신고됐고 무엇을
 * 이유로 내려갔는지"가 한 축에서 읽힌다. 조치 사유에만 있는 것은 운영자 전용 「개인정보 노출」 하나다.
 *
 * <p>문구는 C18 화면과 같다 — 소비자가 고르는 말과 운영자가 보는 말이 같아야 신고 사유별 조치율을
 * 그대로 읽을 수 있다.
 */
public enum PostReportReason {
    MISLEADING_INFO("허위 · 과장 정보", PostSuspensionReason.MISLEADING_INFO),
    FRAUD_IMPERSONATION("사기 · 사칭", PostSuspensionReason.FRAUD_IMPERSONATION),
    ABUSE_HATE("욕설 · 혐오 표현", PostSuspensionReason.ABUSE_HATE),
    SEXUAL_CONTENT("음란물 · 성적인 내용", PostSuspensionReason.SEXUAL_CONTENT),
    SPAM("스팸 · 반복 게시", PostSuspensionReason.SPAM),
    /** 그 밖의 사유 — 상세 설명을 반드시 함께 받는다 */
    OTHER("기타", PostSuspensionReason.OTHER);

    private final String label;
    private final PostSuspensionReason suspensionReason;

    PostReportReason(String label, PostSuspensionReason suspensionReason) {
        this.label = label;
        this.suspensionReason = suspensionReason;
    }

    public String getLabel() {
        return label;
    }

    /** 운영자 조치 화면이 사유를 그대로 이어받는다 — 신고 → 중지가 같은 코드 축에서 읽힌다 */
    public PostSuspensionReason toSuspensionReason() {
        return suspensionReason;
    }

    /**
     * 기타는 상세 설명 없이 접수하지 않는다.
     *
     * <p>운영자가 무엇을 보라는 것인지 알 수 없는 신고는 조치로 이어지지 않고 대기열만 채운다.
     * {@link PostSuspensionReason#requiresDetail()}과 같은 규칙이다.
     */
    public boolean requiresDetail() {
        return this == OTHER;
    }
}
