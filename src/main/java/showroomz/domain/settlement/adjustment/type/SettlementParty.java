package showroomz.domain.settlement.adjustment.type;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import showroomz.domain.message.type.ParticipantType;
import showroomz.domain.settlement.type.SettlementActorType;

/**
 * 정산 조정 협의의 당사자(44 이슈 스레드 설계서 1-3) — 구 {@code FulfillmentSide}(공구 · 폐기 이력)를 끌어오지 않는다.
 * id 규칙은 연결·소통의 보낸 사람과 같다 — 브랜드는 마켓 id, 인플루언서는 크리에이터 id.
 */
@Getter
@RequiredArgsConstructor
public enum SettlementParty {
    SELLER("브랜드"),
    CREATOR("인플루언서");

    private final String label;

    public SettlementParty counterpart() {
        return this == SELLER ? CREATOR : SELLER;
    }

    public ParticipantType participantType() {
        return this == SELLER ? ParticipantType.SELLER : ParticipantType.CREATOR;
    }

    public SettlementActorType actorType() {
        return this == SELLER ? SettlementActorType.SELLER : SettlementActorType.CREATOR;
    }
}
