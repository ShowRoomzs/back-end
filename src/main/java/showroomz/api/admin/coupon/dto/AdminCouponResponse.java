package showroomz.api.admin.coupon.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.*;
import showroomz.domain.coupon.entity.Coupon;
import showroomz.domain.coupon.type.CouponStatus;
import showroomz.domain.coupon.type.DiscountType;

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

    @Schema(description = "쿠폰 코드", example = "WELCOME10")
    private String code;

    @Schema(description = "할인 타입 (FIXED_AMOUNT, PERCENTAGE)", example = "PERCENTAGE")
    private DiscountType discountType;

    @Schema(description = "할인값", example = "10")
    private BigDecimal discountValue;

    @Schema(description = "최소 주문 금액", example = "30000")
    private BigDecimal minimumOrderPrice;

    @Schema(description = "유효 시작 일시", example = "2026-01-01T00:00:00")
    @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd'T'HH:mm:ss")
    private LocalDateTime validFrom;

    @Schema(description = "유효 종료 일시", example = "2026-12-31T23:59:59")
    @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd'T'HH:mm:ss")
    private LocalDateTime validUntil;

    @Schema(description = "총 발급 수량 (null: 무제한)")
    private Integer totalQuantity;

    @Schema(description = "잔여 수량 (null: 무제한)")
    private Integer remainingQuantity;

    @Schema(description = "쿠폰 상태 (ACTIVE, EXPIRED, SCHEDULED)", example = "ACTIVE")
    private CouponStatus status;

    @Schema(description = "등록일", example = "2026-01-01T10:00:00")
    @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd'T'HH:mm:ss")
    private LocalDateTime createdAt;

    public static AdminCouponResponse from(Coupon coupon) {
        LocalDateTime now = LocalDateTime.now();
        CouponStatus status = computeStatus(coupon.getStartAt(), coupon.getEndAt(), now);

        return AdminCouponResponse.builder()
                .couponId(coupon.getId())
                .name(coupon.getName())
                .code(coupon.getCode())
                .discountType(coupon.getDiscountType())
                .discountValue(coupon.getDiscountValue())
                .minimumOrderPrice(coupon.getMinOrderAmount())
                .validFrom(coupon.getStartAt())
                .validUntil(coupon.getEndAt())
                .totalQuantity(null)  // 현재 스키마에 수량 제한 없음
                .remainingQuantity(null)
                .status(status)
                .createdAt(coupon.getCreatedAt())
                .build();
    }

    private static CouponStatus computeStatus(LocalDateTime startAt, LocalDateTime endAt, LocalDateTime now) {
        if (now.isBefore(startAt)) {
            return CouponStatus.SCHEDULED;
        }
        if (now.isAfter(endAt)) {
            return CouponStatus.EXPIRED;
        }
        return CouponStatus.ACTIVE;
    }
}
