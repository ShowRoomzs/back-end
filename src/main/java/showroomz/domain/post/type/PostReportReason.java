package showroomz.domain.post.type;

/**
 * 소비자 신고 사유 코드 (C4 ⋯ 시트 · C5 신고 시트).
 *
 * <p>값을 {@link PostSuspensionReason}과 <b>1:1로 맞춘다.</b> 신고는 조치의 입구이고, 운영자가
 * 신고를 받아 노출 중지를 누르는 순간 사유 코드가 그대로 이어져야 "무엇을 이유로 신고됐고 무엇을
 * 이유로 내려갔는지"가 한 축에서 읽힌다. 두 벌로 두면 매핑 표가 코드 밖에 생기고, 사유별 조치
 * 건수를 셀 때 두 집합을 손으로 이어 붙여야 한다.
 *
 * <p>다른 것은 <b>문구</b>뿐이다. 운영자 쪽은 규정 용어("대가관계 미표시")로, 신고자 쪽은 본 것을
 * 그대로 고르는 말("광고인데 표시가 없어요")로 적는다.
 *
 * <p><b>목록은 잠정이다</b> — C5 §남은 결정 ③ "신고 사유 선택 화면"이 미확정이다. 확정되면 값이
 * 늘거나 줄 수 있고, 그때도 {@link PostSuspensionReason}과의 1:1은 유지한다.
 */
public enum PostReportReason {

    AD_DISCLOSURE("광고인데 표시가 없어요", PostSuspensionReason.AD_DISCLOSURE),

    MEDICAL_CLAIM("의학적 효능을 내세워요", PostSuspensionReason.MEDICAL_CLAIM),

    MISLEADING_AD("허위·과장된 내용이에요", PostSuspensionReason.MISLEADING_AD),

    COPYRIGHT("저작권을 침해했어요", PostSuspensionReason.COPYRIGHT),

    SEXUAL_CONTENT("선정적이에요", PostSuspensionReason.SEXUAL_CONTENT),

    VIOLENCE_HATE("폭력적이거나 혐오스러워요", PostSuspensionReason.VIOLENCE_HATE),

    PERSONAL_INFO("개인정보가 노출됐어요", PostSuspensionReason.PERSONAL_INFO),

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
