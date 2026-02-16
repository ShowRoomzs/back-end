package showroomz.domain.coupon.type;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum DiscountType {
    FIXED_AMOUNT("정액"),
    PERCENTAGE("정률");

    private final String description;
}
