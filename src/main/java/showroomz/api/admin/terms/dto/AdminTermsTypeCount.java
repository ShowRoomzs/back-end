package showroomz.api.admin.terms.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Getter;
import showroomz.api.admin.terms.type.AdminTermsTypeFilter;

@Getter
@AllArgsConstructor
@Schema(description = "유형 탭 건수 (기획 §21-3) — 전체 · 이용 약관 · 개인정보처리방침 · 마케팅 동의 4종")
public class AdminTermsTypeCount {

    @Schema(description = "탭 코드", example = "TERMS_OF_SERVICE")
    private AdminTermsTypeFilter type;

    @Schema(description = "탭 표시명", example = "이용 약관")
    private String displayName;

    @Schema(description = "건수", example = "3")
    private long count;

    public static AdminTermsTypeCount of(AdminTermsTypeFilter type, long count) {
        return new AdminTermsTypeCount(type, type.getDescription(), count);
    }
}
