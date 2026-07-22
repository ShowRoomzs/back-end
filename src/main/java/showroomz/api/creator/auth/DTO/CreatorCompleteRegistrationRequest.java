package showroomz.api.creator.auth.DTO;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import lombok.Getter;
import lombok.Setter;
import showroomz.domain.member.creator.type.CreatorBusinessType;

@Getter
@Setter
@Schema(description = "크리에이터 승인 후 추가 정보 입력 요청")
public class CreatorCompleteRegistrationRequest {

    @NotBlank(message = "쇼룸명은 필수 입력값입니다.")
    @Pattern(
            regexp = "^([가-힣0-9]+|[a-zA-Z0-9]+)$",
            message = "쇼룸명은 공백과 특수문자를 사용할 수 없으며, 한글 또는 영문 중 하나만 사용해야 합니다."
    )
    @Schema(description = "쇼룸명 (중복 불가)", example = "myshowroom")
    private String showroomName;

    @NotNull(message = "사업자 여부는 필수 입력값입니다.")
    @Schema(
            description = "사업자 여부 (INDIVIDUAL: 개인/비사업자, BUSINESS: 개인사업자/법인)",
            example = "BUSINESS",
            allowableValues = {"INDIVIDUAL", "BUSINESS"}
    )
    private CreatorBusinessType businessType;

    @Pattern(
            regexp = "^\\d{3}-\\d{2}-\\d{5}$",
            message = "사업자등록번호 형식이 올바르지 않습니다. (예: 123-45-67890)"
    )
    @Schema(description = "사업자등록번호 (사업자 선택 시 필수)", example = "123-45-67890")
    private String businessRegistrationNumber;

    @Schema(description = "사업자등록증 URL (사업자 선택 시 필수)", example = "https://s3.../license.jpg")
    private String businessLicenseImageUrl;

    @NotBlank(message = "은행명은 필수 입력값입니다.")
    @Schema(description = "은행명", example = "국민은행")
    private String bankName;

    @NotBlank(message = "계좌번호는 필수 입력값입니다.")
    @Pattern(regexp = "^[0-9]+$", message = "계좌번호는 하이픈 없이 숫자만 입력해주세요.")
    @Schema(description = "계좌번호 (하이픈 없이 숫자만)", example = "12345678901234")
    private String accountNumber;

    @NotBlank(message = "통장 사본 URL은 필수 입력값입니다.")
    @Schema(description = "통장 사본 URL", example = "https://s3.../bankbook.jpg")
    private String bankBookImageUrl;
}
