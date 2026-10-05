package showroomz.global.delivery.tracker.sweettracker.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.util.List;

/**
 * {@code POST /api/v1/trackingInfo} 응답 — 정상 응답과 에러 응답({@code status=false · code · msg})이 같은 자리에 온다.
 *
 * @param level    진행 단계 0~6 — 0 은 택배사에 스캔 정보 없음, 6 은 배송 완료
 * @param complete 배송 완료 여부
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record SweetTrackerTrackingResponse(Boolean status, String code, String msg, Integer level, Boolean complete,
                                           List<Detail> trackingDetails, Detail lastDetail) {

    /**
     * @param time       진행 시각(epoch)
     * @param timeString 진행 시각(KST 문자열)
     * @param where      진행 위치
     * @param kind       진행 상태 문구
     */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Detail(Long time, String timeString, Integer level, String kind, String where) {
    }

    public boolean isError() {
        return Boolean.FALSE.equals(status);
    }
}
