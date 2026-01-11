package showroomz.api.admin.market.type;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public enum RejectionReasonType {
    BUSINESS_INFO_UNVERIFIED("사업자정보 확인 불가"),
    CRITERIA_NOT_MET("입점 기준 미달성"),
    INAPPROPRIATE_MARKET_NAME("마켓명 부적절"),
    OTHER("기타(직접 작성)");

    private final String description;
}
