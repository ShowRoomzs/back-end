package showroomz.api.app.coupon.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "쿠폰 등록 요청 (쿠폰 코드로 등록)")
public class CouponRegisterRequest {

    @NotBlank(message = "쿠폰 코드는 필수입니다.")
    @Schema(description = "쿠폰 코드", example = "WELCOME10", requiredMode = Schema.RequiredMode.REQUIRED)
    private String code;
}
