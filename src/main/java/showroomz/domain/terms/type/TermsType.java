package showroomz.domain.terms.type;

import lombok.Getter;

/**
 * 약관 문서 유형 (기획 §21-2) — 3종 고정.
 *
 * <p>유형은 <b>등록 후 고정</b>이다. 유형이 바뀌면 같은 문서로 볼 수 없어 문서를 새로 만들어야 한다.
 */
@Getter
public enum TermsType {

    TERMS_OF_SERVICE("이용 약관"),
    PRIVACY_POLICY("개인정보처리방침"),
    MARKETING_CONSENT("마케팅 동의");

    private final String displayName;

    TermsType(String displayName) {
        this.displayName = displayName;
    }
}
