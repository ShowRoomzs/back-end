package showroomz.global.delivery.tracker.sweettracker.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/** {@code POST /api/v1/key/usage} 응답 — 이용권 기간과 사용량. */
@JsonIgnoreProperties(ignoreUnknown = true)
public record SweetTrackerUsageResponse(Boolean status, String code, String msg, String startDate, String endDate,
                                        Long totalAmount, Long leftAmount) {

    public boolean isError() {
        return Boolean.FALSE.equals(status);
    }
}
