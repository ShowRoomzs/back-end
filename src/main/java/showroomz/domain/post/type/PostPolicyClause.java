package showroomz.domain.post.type;

import java.util.Set;

/**
 * 노출 중지의 근거 규정 조항 (§54-5 — 필수 · 이의 신청 · 반려 심사에서 그 조항이 판단 기준이다).
 *
 * <p><b>가정값이다.</b> 커뮤니티 운영정책 문서가 아직 없어(기획 X-23 · A-59) 조항 번호와 문구가 바뀔 수 있다.
 * 그래서 조치에는 코드와 함께 선택 시점의 문구({@code post_suspension.policy_ref})를 굳힌다 —
 * 문서가 확정되어 값이 바뀌어도 과거 조치의 근거는 그대로 남는다.
 *
 * <p>{@link #suggestedFor}는 FE 기본 선택용 추천일 뿐이다. 사유 ↔ 조항은 다대다이고(허위 · 과장 정보 →
 * 3조 2항 또는 3조 4항) 운영자 판단 영역이라 서버는 조합을 검증하지 않는다.
 */
public enum PostPolicyClause {
    ARTICLE_3_2("커뮤니티 운영정책 3조 2항 — 효과 · 가격 오인 표현",
            Set.of(PostSuspensionReason.MISLEADING_INFO)),
    ARTICLE_3_4("커뮤니티 운영정책 3조 4항 — 의약품 · 의료기기 오인 표현",
            Set.of(PostSuspensionReason.MISLEADING_INFO)),
    ARTICLE_4_1("커뮤니티 운영정책 4조 1항 — 욕설 · 혐오 표현",
            Set.of(PostSuspensionReason.ABUSE_HATE, PostSuspensionReason.SEXUAL_CONTENT)),
    ARTICLE_4_3("커뮤니티 운영정책 4조 3항 — 타인 사칭",
            Set.of(PostSuspensionReason.FRAUD_IMPERSONATION, PostSuspensionReason.PERSONAL_INFO)),
    ARTICLE_5("커뮤니티 운영정책 5조 — 광고 · 도배",
            Set.of(PostSuspensionReason.SPAM));

    private final String label;
    private final Set<PostSuspensionReason> suggestedFor;

    PostPolicyClause(String label, Set<PostSuspensionReason> suggestedFor) {
        this.label = label;
        this.suggestedFor = suggestedFor;
    }

    public String getLabel() {
        return label;
    }

    public Set<PostSuspensionReason> getSuggestedFor() {
        return suggestedFor;
    }
}
