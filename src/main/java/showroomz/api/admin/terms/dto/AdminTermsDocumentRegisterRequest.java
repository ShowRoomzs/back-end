package showroomz.api.admin.terms.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.NoArgsConstructor;
import showroomz.domain.terms.type.TermsTarget;
import showroomz.domain.terms.type.TermsType;

import java.time.LocalDate;

@Getter
@NoArgsConstructor
@Schema(description = "문서 등록(신규) 요청 (기획 §21-5) — 아직 없는 문서를 처음 올린다. "
        + "버전 번호는 받지 않으며 v1.0으로 자동 부여된다.")
public class AdminTermsDocumentRegisterRequest {

    @NotBlank(message = "문서명은 필수 입력값입니다.")
    @Size(max = 100, message = "문서명은 100자 이하로 입력해 주세요.")
    @Schema(description = "문서명 (필수)", example = "소비자 이용약관")
    private String name;

    @NotNull(message = "유형은 필수 입력값입니다.")
    @Schema(description = "유형 (필수) — 등록 후 문서 속성으로 고정된다", example = "TERMS_OF_SERVICE")
    private TermsType type;

    @NotNull(message = "대상은 필수 입력값입니다.")
    @Schema(description = "대상 (필수) — 등록 후 문서 속성으로 고정된다", example = "USER")
    private TermsTarget target;

    @NotNull(message = "시행일은 필수 입력값입니다.")
    @JsonFormat(pattern = "yyyy-MM-dd")
    @Schema(description = "시행일 (필수) — 오늘 이후만 가능하다", example = "2026-09-01", type = "string", format = "date")
    private LocalDate effectiveDate;

    @NotBlank(message = "본문은 필수 입력값입니다.")
    @Schema(description = "본문 (필수) — 등록 후 수정할 수 없다", example = "제1조(목적) ...")
    private String content;
}
