package showroomz.domain.order.type;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/** 배송 권역 — 우편번호 판정(1021 설계서 3-2 · 파트너 06 「권역 판정은 우편번호 기준 · 겹치면 제주 우선」). */
@Getter
@RequiredArgsConstructor
public enum DeliveryRegion {
    STANDARD("일반"),
    JEJU("제주"),
    REMOTE("도서산간");

    private final String label;
}
