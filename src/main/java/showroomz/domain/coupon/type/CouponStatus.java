package showroomz.domain.coupon.type;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * 쿠폰 상태 (startAt, endAt 기준 계산)
 * - ACTIVE: 현재 유효 기간 내
 * - EXPIRED: 만료됨 (현재 시각 > endAt)
 * - SCHEDULED: 예정됨 (현재 시각 < startAt)
 */
@Getter
@RequiredArgsConstructor
public enum CouponStatus {
    ACTIVE("활성"),
    EXPIRED("만료"),
    SCHEDULED("예정");

    private final String description;
}
