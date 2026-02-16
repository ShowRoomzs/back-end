package showroomz.api.app.coupon.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "사용자 쿠폰 등록 응답")
public class UserCouponRegisterResponse {

    @Schema(description = "성공 메시지", example = "쿠폰이 정상적으로 등록되었습니다.", requiredMode = Schema.RequiredMode.REQUIRED)
    private String message;

    @Schema(description = "등록된 사용자 쿠폰 ID (UserCoupon PK)", example = "1", requiredMode = Schema.RequiredMode.REQUIRED)
    private Long userCouponId;

    @Schema(description = "쿠폰 이름", example = "신규 가입 10% 할인", requiredMode = Schema.RequiredMode.REQUIRED)
    private String name;
}
