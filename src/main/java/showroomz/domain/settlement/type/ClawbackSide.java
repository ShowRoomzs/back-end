package showroomz.domain.settlement.type;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/** 차감 측(44 어드민 설계서 0-8) — 브랜드 몫 · 리워드를 따로 회수한다. */
@Getter
@RequiredArgsConstructor
public enum ClawbackSide {
    BRAND("브랜드"),
    CREATOR("인플루언서");

    private final String label;
}
