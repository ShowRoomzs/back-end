package showroomz.api.app.coupon.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import showroomz.domain.coupon.entity.Coupon;
import showroomz.domain.coupon.entity.UserCoupon;
import showroomz.domain.coupon.type.DiscountType;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "상품 결제 시 적용 가능한 사용자 쿠폰 항목")
public class ProductApplicableCouponDto {

    @Schema(description = "사용자 쿠폰 ID", example = "450")
    private Long userCouponId;

    @Schema(description = "쿠폰명", example = "10% 할인")
    private String name;

    @Schema(description = "할인 타입", example = "PERCENTAGE")
    private DiscountType discountType;

    @Schema(description = "할인값 (정액: 원, 정률: %)")
    private BigDecimal discountValue;

    @Schema(description = "최소 주문 금액 (원, null이면 제한 없음)")
    private BigDecimal minimumOrderPrice;

    @Schema(description = "유효 종료 일시")
    @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd'T'HH:mm:ss")
    private LocalDateTime validUntil;

    public static ProductApplicableCouponDto from(UserCoupon userCoupon) {
        Coupon c = userCoupon.getCoupon();
        return ProductApplicableCouponDto.builder()
                .userCouponId(userCoupon.getId())
                .name(c.getName())
                .discountType(c.getDiscountType())
                .discountValue(c.getDiscountValue())
                .minimumOrderPrice(c.getMinOrderAmount())
                .validUntil(c.getEndAt())
                .build();
    }
}
