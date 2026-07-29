package showroomz.api.seller.auth.DTO;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Schema(description = "판매자 승인 후 필수 정보(배송 설정) 입력 요청")
public class SellerCompleteRegistrationRequest {

    @NotBlank(message = "수취인 이름을 입력해 주세요.")
    @Pattern(
            regexp = "^[가-힣a-zA-Z\\s]+$",
            message = "수취인 이름은 한글·영문·공백만 입력할 수 있습니다."
    )
    @Schema(description = "수취인 이름 (한글·영문·공백만)", example = "김담당")
    private String recipientName;

    @NotBlank(message = "수취인 연락처를 입력해 주세요.")
    @Pattern(
            regexp = "^01(?:0|1|[6-9])-\\d{3,4}-\\d{4}$",
            message = "올바른 휴대폰 번호 형식으로 입력해 주세요. (예: 010-1234-5678)"
    )
    @Schema(description = "수취인 연락처 (하이픈 포함)", example = "010-1234-5678")
    private String contact;

    @NotBlank(message = "주소를 검색해 입력해 주세요.")
    @Schema(description = "주소 (우편번호 검색 결과)", example = "서울특별시 강남구 테헤란로 123")
    private String address;

    @NotBlank(message = "상세 주소를 입력해 주세요.")
    @Schema(description = "상세 주소", example = "4층 401호")
    private String detailAddress;

    @NotNull(message = "기본 배송비는 필수 입력값입니다.")
    @Min(value = 0, message = "기본 배송비는 0 이상이어야 합니다.")
    @Schema(description = "기본 배송비", example = "3000")
    private Integer defaultDeliveryFee;

    @Min(value = 0, message = "무료배송 기준금액은 0 이상이어야 합니다.")
    @Schema(description = "무료배송 기준금액 (선택)", example = "50000")
    private Integer freeShippingThreshold;

    @Min(value = 0, message = "도서산간 추가비는 0 이상이어야 합니다.")
    @Schema(description = "도서산간 추가비 (미입력 시 0원)", example = "3000")
    private Integer remoteAreaSurcharge;

    @NotNull(message = "출고 소요일은 필수 입력값입니다.")
    @Min(value = 1, message = "출고 소요일은 1 이상이어야 합니다.")
    @Schema(description = "출고 소요일", example = "3")
    private Integer shippingLeadDays;

    @NotNull(message = "반품비는 필수 입력값입니다.")
    @Min(value = 0, message = "반품비는 0 이상이어야 합니다.")
    @Schema(description = "반품비(회수비) (기본값 3000)", example = "3000")
    private Integer returnFee;

    @NotNull(message = "교환비는 필수 입력값입니다.")
    @Min(value = 0, message = "교환비는 0 이상이어야 합니다.")
    @Schema(description = "교환비 (기본값 6000)", example = "6000")
    private Integer exchangeFee;
}
