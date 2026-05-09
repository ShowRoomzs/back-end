package showroomz.domain.coupon.type;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum CouponStatus {
    WAITING("대기"),
    ACTIVE("적용중"),
    EXPIRED("만료"),
    STOPPED("중지");

    private final String description;
}
