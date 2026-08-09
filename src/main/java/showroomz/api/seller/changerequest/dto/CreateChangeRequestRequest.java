package showroomz.api.seller.changerequest.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import showroomz.domain.changerequest.type.ChangeRequestType;

import java.util.List;

@Getter
@Setter
@NoArgsConstructor
@Schema(description = "변경 요청 생성(M1·M2 공통)")
public class CreateChangeRequestRequest {

    @NotNull(message = "요청 유형은 필수입니다.")
    @Schema(description = "요청 유형", example = "BUSINESS_INFO")
    private ChangeRequestType type;

    @NotEmpty(message = "변경 항목을 선택해주세요.")
    @Valid
    @Schema(description = "변경 항목 목록. SETTLEMENT_ACCOUNT는 은행·계좌번호·예금주 3개 고정이다.")
    private List<ChangeRequestItemRequest> items;

    @Size(max = 500, message = "변경 사유는 500자 이내로 입력해주세요.")
    @Schema(description = "변경 사유. BUSINESS_INFO는 필수, SETTLEMENT_ACCOUNT는 선택.")
    private String reason;

    @NotNull(message = "증빙 파일 URL은 필수입니다.")
    @Schema(description = "증빙 파일 URL(기존 POST /v1/seller/images 업로드 결과)")
    private String evidenceFileUrl;

    @NotNull(message = "증빙 파일명은 필수입니다.")
    @Schema(description = "증빙 파일 원본 파일명(어드민 파일칩 표시용)", example = "사업자등록증_변경.jpg")
    private String evidenceFileName;

    @NotNull(message = "증빙 파일 크기는 필수입니다.")
    @Schema(description = "증빙 파일 크기(byte)", example = "1258291")
    private Long evidenceFileSize;
}
