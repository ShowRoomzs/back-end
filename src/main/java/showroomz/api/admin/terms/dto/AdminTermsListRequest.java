package showroomz.api.admin.terms.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Getter;
import lombok.Setter;
import showroomz.api.admin.terms.type.AdminTermsTypeFilter;

@Getter
@Setter
public class AdminTermsListRequest {

    @Schema(description = "유형 탭 (미입력 시 전체)", example = "TERMS_OF_SERVICE",
            allowableValues = {"ALL", "TERMS_OF_SERVICE", "PRIVACY_POLICY", "MARKETING_CONSENT"})
    private AdminTermsTypeFilter type = AdminTermsTypeFilter.ALL;

    @Schema(description = "문서명 키워드 검색 (문서명 단일 대상)", example = "이용약관")
    private String keyword;

    /** 미입력이면 기본 진입 탭인 전체로 본다 (기획 §21-3) */
    public AdminTermsTypeFilter getType() {
        return type == null ? AdminTermsTypeFilter.ALL : type;
    }
}
