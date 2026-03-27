package showroomz.domain.coupon.type;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum UserCouponStatus {
    AVAILABLE("사용 전"),
    USED("사용 완료");

    private final String description;
}
