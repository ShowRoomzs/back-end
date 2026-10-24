package showroomz.domain.post.type;

import java.util.Set;

/**
 * 노출 중지의 근거 규정 조항 (§54-5 — 필수 · 이의 신청 · 반려 심사에서 그 조항이 판단 기준이다).
 *
 * <p>조항은 <b>서비스 운영정책의 커뮤니티 장</b>에 있다(rev.7 시안 · 55 계획서 A5). 라벨은 {@link #POLICY_CHAPTER} + 조항이고,
 * 문구는 노출 중지 모달 드롭다운 기준이다(상세 화면 문구와 미세하게 다르다).
 *
 * <p><b>조항 번호와 문구는 가정값이다.</b> 운영정책 문서가 아직 없어(기획 X-23 · A-59) 바뀔 수 있다.
 * 그래서 조치에는 코드와 함께 선택 시점의 문구({@code post_suspension.policy_ref})를 굳힌다 —
 * 문서가 확정되어 값이 바뀌어도 과거 조치의 근거는 그대로 남는다. 라벨을 바꿔도 과거 행은 소급하지 않는다(55 계획서 D20).
 *
 * <p>{@link #suggestedFor}는 FE 기본 선택용 추천일 뿐이다. 사유 ↔ 조항은 다대다이고(허위 · 과장 정보 →
 * 3조 2항 또는 3조 4항) 운영자 판단 영역이라 서버는 조합을 검증하지 않는다.
 */
public enum PostPolicyClause {
    ARTICLE_3_2("3조 2항 — 효과 · 가격 오인 표현",
            Set.of(PostSuspensionReason.MISLEADING_INFO)),
    ARTICLE_3_4("3조 4항 — 의약품 · 의료기기 오인 표현",
            Set.of(PostSuspensionReason.MISLEADING_INFO)),
    ARTICLE_4_1("4조 1항 — 욕설 · 혐오 표현",
            Set.of(PostSuspensionReason.ABUSE_HATE, PostSuspensionReason.SEXUAL_CONTENT)),
    ARTICLE_4_3("4조 3항 — 타인 사칭",
            Set.of(PostSuspensionReason.FRAUD_IMPERSONATION, PostSuspensionReason.PERSONAL_INFO)),
    ARTICLE_5("5조 — 광고 · 도배",
            Set.of(PostSuspensionReason.SPAM));

    /** 조항이 실린 문서 · 장 — 라벨 머리말이자 운영 지표 「게시물 신고율」의 근거 표기다(55 계획서 A5 · A15). */
    public static final String POLICY_CHAPTER = "서비스 운영정책 — 커뮤니티 장";

    private final String label;
    private final Set<PostSuspensionReason> suggestedFor;

    PostPolicyClause(String clause, Set<PostSuspensionReason> suggestedFor) {
        this.label = POLICY_CHAPTER + " " + clause;
        this.suggestedFor = suggestedFor;
    }

    public String getLabel() {
        return label;
    }

    public Set<PostSuspensionReason> getSuggestedFor() {
        return suggestedFor;
    }
}
