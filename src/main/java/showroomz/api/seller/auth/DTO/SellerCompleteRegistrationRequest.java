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

    @NotBlank(message = "담당자명(수취인)은 필수 입력값입니다.")
    @Schema(description = "담당자명(수취인)", example = "김담당")
    private String recipientName;

    @NotBlank(message = "연락처는 필수 입력값입니다.")
    @Pattern(
            regexp = "^01(?:0|1|[6-9])(?:\\d{3}|\\d{4})\\d{4}$|^01(?:0|1|[6-9])-(?:\\d{3}|\\d{4})-\\d{4}$",
            message = "올바른 휴대폰 번호 형식이 아닙니다."
    )
    @Schema(description = "연락처", example = "010-1234-5678")
    private String contact;

    @NotBlank(message = "주소는 필수 입력값입니다.")
    @Schema(description = "주소", example = "서울특별시 강남구 테헤란로 123")
    private String address;

    @NotBlank(message = "상세 주소는 필수 입력값입니다.")
    @Schema(description = "상세 주소", example = "4층 401호")
    private String detailAddress;

    @NotNull(message = "기본 배송비는 필수 입력값입니다.")
    @Min(value = 0, message = "기본 배송비는 0 이상이어야 합니다.")
    @Schema(description = "기본 배송비", example = "3000")
    private Integer defaultDeliveryFee;

    @Min(value = 0, message = "무료배송 기준금액은 0 이상이어야 합니다.")
    @Schema(description = "무료배송 기준금액 (미입력 시 0원)", example = "50000")
    private Integer freeShippingThreshold;

    @Min(value = 0, message = "도서산간 추가비는 0 이상이어야 합니다.")
    @Schema(description = "도서산간 추가비 (미입력 시 0원)", example = "3000")
    private Integer remoteAreaSurcharge;

    @NotNull(message = "출고 소요일은 필수 입력값입니다.")
    @Min(value = 1, message = "출고 소요일은 1 이상이어야 합니다.")
    @Schema(description = "출고 소요일", example = "3")
    private Integer shippingLeadDays;

    @Min(value = 0, message = "반품비는 0 이상이어야 합니다.")
    @Schema(description = "반품비 (미입력 시 3000원)", example = "3000")
    private Integer returnFee;

    @Min(value = 0, message = "교환비는 0 이상이어야 합니다.")
    @Schema(description = "교환비 (미입력 시 6000원)", example = "6000")
    private Integer exchangeFee;
}
