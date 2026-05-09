package showroomz.api.admin.coupon.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Getter;
import lombok.NoArgsConstructor;
@Getter
@NoArgsConstructor
@Schema(description = "관리자 쿠폰 수정 요청")
public class AdminCouponUpdateRequest extends AdminCouponCreateRequest {}
