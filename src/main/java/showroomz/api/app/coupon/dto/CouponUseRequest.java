package showroomz.api.app.coupon.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Getter
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "쿠폰 적용 요청 (현재 주문 금액 기준 할인 계산)")
public class CouponUseRequest {

    @NotNull(message = "주문 금액은 필수입니다.")
    @Positive(message = "주문 금액은 0보다 커야 합니다.")
    @Schema(description = "적용 기준이 되는 주문 금액(원)", example = "65000", requiredMode = Schema.RequiredMode.REQUIRED)
    private BigDecimal orderAmount;
}
