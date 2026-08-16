package showroomz.domain.post.type;

/**
 * 노출 중지 사유 코드 (§24-5 "사유 · 근거 규정 · 조치 시각 · 처리자 · 기한을 화면에 남긴다").
 *
 * <p>자유 문구가 아니라 코드로 두는 이유 — 통지 문구를 서버가 굳혀야 하고(§24-6 알림 이력 영구 보존),
 * 나중에 사유별 조치 건수를 세려면 값이 정규화되어 있어야 한다. 코드로 표현되지 않는 사안은
 * {@link #OTHER}에 상세 설명을 함께 받는다.
 */
public enum PostSuspensionReason {

    /** 의학적 효능·효과 표방 */
    MEDICAL_CLAIM("의학적 효능 표방"),

    /** 대가관계(광고) 미표시 */
    AD_DISCLOSURE("대가관계 미표시"),

    /** 저작권 침해 */
    COPYRIGHT("저작권 침해"),

    /** 허위·과장 광고 */
    MISLEADING_AD("허위·과장 광고"),

    /** 선정성 */
    SEXUAL_CONTENT("선정적 콘텐츠"),

    /** 폭력·혐오 표현 */
    VIOLENCE_HATE("폭력·혐오 표현"),

    /** 타인의 개인정보 노출 */
    PERSONAL_INFO("개인정보 노출"),

    /** 그 밖의 사유 — 상세 설명을 반드시 함께 받는다 */
    OTHER("기타");

    private final String label;

    PostSuspensionReason(String label) {
        this.label = label;
    }

    public String getLabel() {
        return label;
    }

    /** 기타 사유는 상세 설명 없이 통지하면 §24-5의 "사유 고지"가 성립하지 않는다. */
    public boolean requiresDetail() {
        return this == OTHER;
    }
}
