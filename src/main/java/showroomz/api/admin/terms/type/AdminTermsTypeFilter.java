package showroomz.api.admin.terms.type;

import lombok.AllArgsConstructor;
import lombok.Getter;
import showroomz.domain.terms.type.TermsType;

/** 목록 유형 탭 (기획 §21-3) — 기본 진입 탭은 전체다. */
@Getter
@AllArgsConstructor
public enum AdminTermsTypeFilter {

    ALL("전체", null),
    TERMS_OF_SERVICE("이용 약관", TermsType.TERMS_OF_SERVICE),
    PRIVACY_POLICY("개인정보처리방침", TermsType.PRIVACY_POLICY),
    MARKETING_CONSENT("마케팅 동의", TermsType.MARKETING_CONSENT);

    private final String description;
    private final TermsType type;
}
