package showroomz.domain.settlement.type;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/** 지급 행의 수취자(44 어드민 설계서 1-1). PG 수수료는 행이 아니라 금액 컬럼이다 — 지급 대상이 아니다. */
@Getter
@RequiredArgsConstructor
public enum SettlementPayee {
    BRAND("브랜드"),
    CREATOR("인플루언서"),
    PLATFORM("플랫폼");

    private final String label;
}
