package showroomz.api.admin.coupon.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import showroomz.domain.coupon.type.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Getter
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "관리자 쿠폰 생성 요청")
public class AdminCouponCreateRequest {

    @NotBlank @Size(max = 100)
    private String name;

    @NotNull
    private CouponType couponType;

    @NotNull
    private TargetAudience targetAudience;

    private Long showroomId;

    @NotNull
    private Boolean isQuantityLimited;

    @NotNull
    private DiscountUnit discountUnit;

    @NotNull
    @DecimalMin(value = "0", inclusive = false)
    private BigDecimal discountValue;

    @DecimalMin(value = "0")
    private BigDecimal minOrderAmount;

    private Integer maxDiscountAmount;

    @NotNull
    private Boolean isMinOrderAmountLimited;

    @NotNull
    private LocalDateTime issueStartDate;

    @NotNull
    private LocalDateTime issueEndDate;

    @NotNull
    private ValidityType validityType;

    private LocalDateTime validStartDate;

    private LocalDateTime validEndDate;

    private Integer validDays;

    @NotNull
    private CouponStatus status;

    @Min(1)
    private Integer totalQuantity;

    private Long sellerId;
}
