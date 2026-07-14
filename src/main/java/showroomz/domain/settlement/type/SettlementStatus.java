package showroomz.domain.settlement.type;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum SettlementStatus {
    PENDING("정산대기"),
    ADMIN_CONFIRMED("운영자확인"),
    TRANSFERRED("이체완료"),
    ON_HOLD("보류");

    private final String description;
}
