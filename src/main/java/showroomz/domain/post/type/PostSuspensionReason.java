package showroomz.domain.post.type;

/**
 * 노출 중지 사유 코드 (§24-5 · §54-5 "사유 · 근거 규정 · 조치 시각 · 처리자 · 기한을 화면에 남긴다").
 *
 * <p><b>소비자 신고 사유 6종({@link PostReportReason})과 같은 축 + 운영자 전용 1종</b>이다(§54 rev.3).
 * 신고를 받아 내리는 순간 사유가 그대로 이어져야 신고 사유별 조치율이 한 축에서 나온다.
 * 운영자 전용인 {@link #PERSONAL_INFO}만 신고로 들어오지 않는다.
 *
 * <p>「의료적 효능 표현」은 별도 사유가 아니라 {@link #MISLEADING_INFO}의 근거 규정(3조 4항 · 의약품 오인)으로
 * 구분한다. 숨은 협찬(대가관계 미표시)도 일반 게시물은 광고가 아니므로 허위 · 과장 정보로 받는다(§54-7 ⓖ).
 *
 * <p>자유 문구가 아니라 코드로 두는 이유 — 통지 문구를 서버가 굳혀야 하고(§24-6 알림 이력 영구 보존),
 * 사유별 조치 건수를 세려면 값이 정규화되어 있어야 한다. 코드로 표현되지 않는 사안은
 * {@link #OTHER}에 상세 설명을 함께 받는다.
 */
public enum PostSuspensionReason {
    /** 허위 · 과장 정보 — 효능 단정 · 의약품 오인 · 숨은 협찬 포함 */
    MISLEADING_INFO("허위 · 과장 정보", false),
    /** 사기 · 사칭 */
    FRAUD_IMPERSONATION("사기 · 사칭", false),
    /** 욕설 · 혐오 표현 */
    ABUSE_HATE("욕설 · 혐오 표현", false),
    /** 음란물 · 성적인 내용 */
    SEXUAL_CONTENT("음란물 · 성적인 내용", false),
    /** 스팸 · 반복 게시 */
    SPAM("스팸 · 반복 게시", false),
    /** 타인의 개인정보 노출 — 신고로 들어오지 않는 운영자 전용 사유 */
    PERSONAL_INFO("개인정보 노출", true),
    /** 그 밖의 사유 — 상세 설명을 반드시 함께 받는다 */
    OTHER("기타", false);

    private final String label;
    private final boolean operatorOnly;

    PostSuspensionReason(String label, boolean operatorOnly) {
        this.label = label;
        this.operatorOnly = operatorOnly;
    }

    public String getLabel() {
        return label;
    }

    /** 소비자 신고 사유에 대응값이 없는 운영자 전용 사유 — 드롭다운에 「(운영자 전용)」을 붙인다 */
    public boolean operatorOnly() {
        return operatorOnly;
    }

    /** 기타 사유는 상세 설명 없이 통지하면 §24-5의 "사유 고지"가 성립하지 않는다. */
    public boolean requiresDetail() {
        return this == OTHER;
    }
}
