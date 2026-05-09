package showroomz.api.admin.coupon.dto;

import jakarta.validation.constraints.NotEmpty;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.util.List;

@Getter
@NoArgsConstructor
public class AdminCouponBulkRequest {
    @NotEmpty
    private List<Long> couponIds;
}
