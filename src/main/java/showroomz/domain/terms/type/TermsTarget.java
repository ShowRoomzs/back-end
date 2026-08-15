package showroomz.domain.terms.type;

import lombok.Getter;

/**
 * 약관 문서 대상 (기획 §21-2) — 4종 고정.
 *
 * <p>대상은 <b>등록 후 고정</b>이다 — 대상이 바뀌면 동의 대상 집단이 달라지므로 같은 문서가 아니다.
 * 마케팅 동의를 대상별로 문서를 따로 두는 이유도 같다(동의 시점·범위가 각각 다르다).
 */
@Getter
public enum TermsTarget {

    ALL("전체"),
    USER("소비자"),
    BRAND("브랜드"),
    INFLUENCER("인플루언서");

    private final String displayName;

    TermsTarget(String displayName) {
        this.displayName = displayName;
    }
}
