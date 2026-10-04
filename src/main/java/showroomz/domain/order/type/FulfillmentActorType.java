package showroomz.domain.order.type;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/** 처리 이력의 주체(§34-9) — 배송 추적(TRACKER)은 시스템과 구분해 적는다. */
@Getter
@RequiredArgsConstructor
public enum FulfillmentActorType {

    SYSTEM("시스템"),
    SELLER("브랜드"),
    ADMIN("운영자"),
    CONSUMER("소비자"),
    TRACKER("배송 추적");

    private final String label;
}
