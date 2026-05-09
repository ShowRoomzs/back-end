package showroomz.api.admin.coupon.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.*;
import showroomz.domain.coupon.entity.Coupon;
import showroomz.domain.coupon.type.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "관리자 쿠폰 목록 조회 응답")
public class AdminCouponResponse {

    @Schema(description = "쿠폰 ID", example = "1")
    private Long couponId;

    @Schema(description = "쿠폰명", example = "신규 가입 10% 할인")
    private String name;

    private String couponIssueNumber;

    private CouponType couponType;

    private TargetAudience targetAudience;

    private DiscountUnit discountUnit;

    private BigDecimal discountValue;

    private BigDecimal minOrderAmount;

    private LocalDateTime issueStartDate;

    private LocalDateTime issueEndDate;

    private CouponStatus status;

    private LocalDateTime createdAt;

    public static AdminCouponResponse from(Coupon coupon) {
        return AdminCouponResponse.builder()
                .couponId(coupon.getId())
                .name(coupon.getName())
                .couponIssueNumber(coupon.getCouponIssueNumber())
                .couponType(coupon.getCouponType())
                .targetAudience(coupon.getTargetAudience())
                .discountUnit(coupon.getDiscountUnit())
                .discountValue(coupon.getDiscountValue())
                .minOrderAmount(coupon.getMinOrderAmount())
                .issueStartDate(coupon.getIssueStartDate())
                .issueEndDate(coupon.getIssueEndDate())
                .status(coupon.getStatus())
                .createdAt(coupon.getCreatedAt())
                .build();
    }
}
