package showroomz.api.seller.coupon.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import showroomz.domain.coupon.type.DiscountType;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

@Getter
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "판매자 쿠폰 등록 요청")
public class SellerCouponCreateRequest {

    @NotBlank(message = "쿠폰 이름은 필수입니다.")
    @Size(max = 100)
    @Schema(description = "쿠폰 이름", example = "봄맞이 10% 할인", requiredMode = Schema.RequiredMode.REQUIRED, maxLength = 100)
    private String name;

    @NotBlank(message = "쿠폰 코드는 필수입니다.")
    @Size(max = 50)
    @Schema(description = "쿠폰 코드 (전역 유일)", example = "SPRING2026", requiredMode = Schema.RequiredMode.REQUIRED, maxLength = 50)
    private String couponCode;

    @NotNull(message = "할인 타입은 필수입니다.")
    @Schema(description = "할인 타입 (FIXED_AMOUNT, PERCENTAGE)", example = "PERCENTAGE", requiredMode = Schema.RequiredMode.REQUIRED)
    private DiscountType discountType;

    @NotNull(message = "할인값은 필수입니다.")
    @DecimalMin(value = "0", inclusive = false, message = "할인값은 0보다 커야 합니다.")
    @Schema(description = "할인값 (정액: 원, 정률: %)", example = "10", requiredMode = Schema.RequiredMode.REQUIRED)
    private BigDecimal discountValue;

    @Schema(description = "최소 주문 금액 (원, null이면 제한 없음)", example = "30000")
    @DecimalMin(value = "0", message = "최소 주문 금액은 0 이상이어야 합니다.")
    private BigDecimal minOrderAmount;

    @Schema(description = "최대 할인 금액 (원, 정률일 때만, null이면 제한 없음)", example = "10000")
    @DecimalMin(value = "0", message = "최대 할인 금액은 0 이상이어야 합니다.")
    private BigDecimal maxDiscountAmount;

    @NotNull(message = "유효 시작 일시는 필수입니다.")
    @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd'T'HH:mm:ss")
    @Schema(description = "유효 시작 일시", example = "2026-01-01T00:00:00", requiredMode = Schema.RequiredMode.REQUIRED)
    private LocalDateTime validFrom;

    @NotNull(message = "유효 종료 일시는 필수입니다.")
    @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd'T'HH:mm:ss")
    @Schema(description = "유효 종료 일시", example = "2026-12-31T23:59:59", requiredMode = Schema.RequiredMode.REQUIRED)
    private LocalDateTime validTo;

    @Min(value = 1, message = "총 발급 수량은 1 이상이어야 합니다.")
    @Schema(description = "총 발급 수량 (null이면 무제한)", example = "500")
    private Integer totalQuantity;

    @NotEmpty(message = "적용할 상품을 1개 이상 선택해야 합니다.")
    @Schema(description = "쿠폰 적용 대상 상품 ID 목록 (본인 소유 상품만)", requiredMode = Schema.RequiredMode.REQUIRED)
    private List<Long> productIds;
}
