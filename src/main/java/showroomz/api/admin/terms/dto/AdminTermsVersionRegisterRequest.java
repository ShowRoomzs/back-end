package showroomz.api.admin.terms.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDate;

@Getter
@NoArgsConstructor
@Schema(description = "새 버전 등록(개정) 요청 (기획 §21-5) — 문서명·유형·대상은 문서 속성이라 받지 않는다. "
        + "등록 후 상태는 시행 예정이며, 시행일 00:00에 서버 배치가 교체한다.")
public class AdminTermsVersionRegisterRequest {

    @NotBlank(message = "버전 번호는 필수 입력값입니다.")
    @Schema(description = "버전 번호 (필수) — 숫자와 점만. 접두 `v`는 화면 표기라 값에 포함하지 않는다",
            example = "3.2")
    private String versionNumber;

    @NotNull(message = "시행일은 필수 입력값입니다.")
    @JsonFormat(pattern = "yyyy-MM-dd")
    @Schema(description = "시행일 (필수) — 오늘 이후만 가능하다", example = "2026-09-01", type = "string", format = "date")
    private LocalDate effectiveDate;

    @NotBlank(message = "본문은 필수 입력값입니다.")
    @Schema(description = "본문 (필수) — 등록 후 수정할 수 없다", example = "제1조(목적) ...")
    private String content;
}
