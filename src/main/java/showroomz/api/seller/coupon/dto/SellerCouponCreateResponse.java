package showroomz.api.seller.coupon.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "판매자 쿠폰 등록 응답")
public class SellerCouponCreateResponse {

    @Schema(description = "생성된 쿠폰 ID", example = "1")
    private Long couponId;

    @Schema(description = "결과 메시지", example = "쿠폰이 등록되었습니다.")
    private String message;
}
