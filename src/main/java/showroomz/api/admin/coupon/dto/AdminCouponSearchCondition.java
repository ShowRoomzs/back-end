package showroomz.api.admin.coupon.dto;

import lombok.Getter;
import lombok.Setter;
import org.springframework.format.annotation.DateTimeFormat;
import showroomz.domain.coupon.type.CouponStatus;
import showroomz.domain.coupon.type.TargetAudience;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Getter
@Setter
public class AdminCouponSearchCondition {
    private String searchType;
    private String keyword;
    private TargetAudience targetAudience;
    private CouponStatus status;
    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
    private LocalDate dateFrom;
    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
    private LocalDate dateTo;

    public LocalDateTime dateFromAtStartOfDay() {
        return dateFrom == null ? null : dateFrom.atStartOfDay();
    }

    public LocalDateTime dateToAtEndOfDay() {
        return dateTo == null ? null : dateTo.plusDays(1).atStartOfDay().minusNanos(1);
    }
}
