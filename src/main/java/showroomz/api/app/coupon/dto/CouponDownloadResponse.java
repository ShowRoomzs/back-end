package showroomz.api.app.coupon.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "사용자 쿠폰 다운로드(발급) 응답")
public class CouponDownloadResponse {

    @Schema(description = "발급된 사용자 쿠폰 ID (UserCoupon PK)", example = "1", requiredMode = Schema.RequiredMode.REQUIRED)
    private Long userCouponId;

    @Schema(description = "성공 메시지", example = "쿠폰이 성공적으로 발급되었습니다.", requiredMode = Schema.RequiredMode.REQUIRED)
    private String message;
}
