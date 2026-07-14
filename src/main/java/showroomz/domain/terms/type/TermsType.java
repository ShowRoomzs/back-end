package showroomz.domain.terms.type;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum TermsType {
    USER_TERMS("소비자 이용약관"),
    PRIVACY_POLICY("개인정보처리방침"),
    SELLER_TERMS("브랜드(판매자) 이용약관"),
    CREATOR_TERMS("인플루언서 이용약관");

    private final String description;
}
