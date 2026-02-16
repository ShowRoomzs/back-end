package showroomz.api.app.coupon.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import showroomz.domain.coupon.entity.UserCoupon;
import showroomz.domain.coupon.type.DiscountType;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Getter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Schema(description = "사용자 쿠폰 카드 정보 (목록/상세 응답용)")
public class UserCouponDto {

    @Schema(description = "사용자 쿠폰 ID (UserCoupon PK)", example = "1", requiredMode = Schema.RequiredMode.REQUIRED)
    private Long userCouponId;

    @Schema(description = "쿠폰 ID (Coupon PK)", example = "1", requiredMode = Schema.RequiredMode.REQUIRED)
    private Long couponId;

    @Schema(description = "쿠폰 이름", example = "신규 가입 10% 할인", requiredMode = Schema.RequiredMode.REQUIRED)
    private String name;

    @Schema(description = "쿠폰 코드 (노출용)", example = "WELCOME10", requiredMode = Schema.RequiredMode.REQUIRED)
    private String couponCode;

    @Schema(description = "할인 타입 (FIXED_AMOUNT: 정액, PERCENTAGE: 정률)", example = "PERCENTAGE", requiredMode = Schema.RequiredMode.REQUIRED)
    private DiscountType discountType;

    @Schema(description = "할인값 (정액: 원, 정률: %)", example = "10", requiredMode = Schema.RequiredMode.REQUIRED)
    private BigDecimal discountValue;

    @Schema(description = "최소 주문 금액 (원, null이면 제한 없음)", example = "30000")
    private BigDecimal minOrderAmount;

    @Schema(description = "최대 할인 금액 (원, 정률일 때만 사용, null이면 제한 없음)", example = "10000")
    private BigDecimal maxDiscountAmount;

    @Schema(description = "쿠폰 유효 시작 일시", example = "2025-01-01T00:00:00", requiredMode = Schema.RequiredMode.REQUIRED)
    @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd'T'HH:mm:ss")
    private LocalDateTime validStartAt;

    @Schema(description = "쿠폰 유효 종료 일시", example = "2025-12-31T23:59:59", requiredMode = Schema.RequiredMode.REQUIRED)
    @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd'T'HH:mm:ss")
    private LocalDateTime validEndAt;

    @Schema(description = "쿠폰 등록 일시", example = "2025-01-15T14:30:00")
    @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd'T'HH:mm:ss")
    private LocalDateTime registeredAt;

    public static UserCouponDto from(UserCoupon userCoupon) {
        var coupon = userCoupon.getCoupon();
        return UserCouponDto.builder()
                .userCouponId(userCoupon.getId())
                .couponId(coupon.getId())
                .name(coupon.getName())
                .couponCode(coupon.getCode())
                .discountType(coupon.getDiscountType())
                .discountValue(coupon.getDiscountValue())
                .minOrderAmount(coupon.getMinOrderAmount())
                .maxDiscountAmount(coupon.getMaxDiscountAmount())
                .validStartAt(coupon.getStartAt())
                .validEndAt(coupon.getEndAt())
                .registeredAt(userCoupon.getRegisteredAt())
                .build();
    }
}
