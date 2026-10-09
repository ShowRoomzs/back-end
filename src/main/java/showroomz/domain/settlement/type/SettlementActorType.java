package showroomz.domain.settlement.type;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/** 이력 주체(44 어드민 설계서 1-1). 확정 주체는 시스템뿐이다 — 「정산 확정 · 운영자」 이력은 없다(12절 #1). */
@Getter
@RequiredArgsConstructor
public enum SettlementActorType {
    SYSTEM("시스템"),
    PG("PG"),
    ADMIN("운영자"),
    SELLER("브랜드"),
    CREATOR("인플루언서");

    private final String label;
}
