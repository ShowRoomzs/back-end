package showroomz.api.admin.coupon.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
@AllArgsConstructor
public class AdminCouponBulkResponse {
    private int affectedCount;
    private String message;
}
