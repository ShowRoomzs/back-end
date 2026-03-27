package showroomz.api.common.coupon.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.Getter;
import showroomz.domain.coupon.type.DiscountType;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Getter
@Builder
@Schema(description = "상품에 매핑된 발급 가능 쿠폰 항목")
public class CommonProductCouponItem {

    @Schema(description = "쿠폰 ID", example = "1")
    private final Long couponId;

    @Schema(description = "쿠폰명", example = "봄맞이 10% 할인")
    private final String name;

    @Schema(description = "할인 유형", example = "PERCENT")
    private final DiscountType discountType;

    @Schema(description = "할인 값 (정액/정률)", example = "10")
    private final BigDecimal discountValue;

    @Schema(description = "최소 주문 금액", example = "30000")
    private final BigDecimal minimumOrderPrice;

    @Schema(description = "유효 종료 일시 (validUntil)")
    private final LocalDateTime validUntil;

    @Schema(description = "현재 로그인 사용자 기준 다운로드(발급) 여부. 비회원은 항상 false")
    private final Boolean isDownloaded;
}
