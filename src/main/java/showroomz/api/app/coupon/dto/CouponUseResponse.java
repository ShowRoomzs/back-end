package showroomz.api.app.coupon.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "쿠폰 적용 응답 (할인액 및 최종 주문 금액)")
public class CouponUseResponse {

    @Schema(description = "적용한 사용자 쿠폰 ID", example = "450", requiredMode = Schema.RequiredMode.REQUIRED)
    private Long userCouponId;

    @Schema(description = "할인 금액(원)", example = "5000", requiredMode = Schema.RequiredMode.REQUIRED)
    private BigDecimal discountAmount;

    @Schema(description = "할인 적용 후 최종 주문 금액(원)", example = "60000", requiredMode = Schema.RequiredMode.REQUIRED)
    private BigDecimal finalOrderAmount;

    @Schema(description = "성공 메시지", example = "쿠폰이 성공적으로 적용되었습니다.", requiredMode = Schema.RequiredMode.REQUIRED)
    private String message;
}
