package showroomz.api.admin.coupon.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import showroomz.domain.coupon.entity.Coupon;
import showroomz.domain.coupon.type.CouponStatus;
import showroomz.domain.coupon.type.CouponType;
import showroomz.domain.coupon.type.DiscountUnit;
import showroomz.domain.coupon.type.TargetAudience;
import showroomz.domain.coupon.type.ValidityType;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.List;

@Getter
@Builder
@AllArgsConstructor
public class AdminCouponDetailResponse {
    private Long couponId;
    private String couponIssueNumber;
    private String name;
    private CouponType couponType;
    private TargetAudience targetAudience;
    private Long showroomId;
    private DiscountUnit discountUnit;
    private BigDecimal discountValue;
    private BigDecimal minOrderAmount;
    private Integer maxDiscountAmount;
    private LocalDateTime issueStartDate;
    private LocalDateTime issueEndDate;
    private ValidityType validityType;
    private LocalDateTime validStartDate;
    private LocalDateTime validEndDate;
    private Integer validDays;
    private CouponStatus status;
    private long issuedCount;
    private long usedCount;
    private BigDecimal usageRate;
    private List<ShowroomAcceptance> showroomAcceptanceList;

    public static AdminCouponDetailResponse from(Coupon coupon, long issuedCount, long usedCount,
                                                 List<ShowroomAcceptance> showroomAcceptanceList) {
        BigDecimal usageRate = BigDecimal.ZERO;
        if (issuedCount > 0) {
            usageRate = BigDecimal.valueOf((double) usedCount * 100 / issuedCount)
                    .setScale(1, RoundingMode.HALF_UP);
        }
        return AdminCouponDetailResponse.builder()
                .couponId(coupon.getId())
                .couponIssueNumber(coupon.getCouponIssueNumber())
                .name(coupon.getName())
                .couponType(coupon.getCouponType())
                .targetAudience(coupon.getTargetAudience())
                .showroomId(coupon.getShowroomId())
                .discountUnit(coupon.getDiscountUnit())
                .discountValue(coupon.getDiscountValue())
                .minOrderAmount(coupon.getMinOrderAmount())
                .maxDiscountAmount(coupon.getMaxDiscountAmount())
                .issueStartDate(coupon.getIssueStartDate())
                .issueEndDate(coupon.getIssueEndDate())
                .validityType(coupon.getValidityType())
                .validStartDate(coupon.getValidStartDate())
                .validEndDate(coupon.getValidEndDate())
                .validDays(coupon.getValidDays())
                .status(coupon.getStatus())
                .issuedCount(issuedCount)
                .usedCount(usedCount)
                .usageRate(usageRate)
                .showroomAcceptanceList(showroomAcceptanceList)
                .build();
    }

    @Getter
    @Builder
    @AllArgsConstructor
    public static class ShowroomAcceptance {
        private Long showroomId;
        private String status;
    }
}
