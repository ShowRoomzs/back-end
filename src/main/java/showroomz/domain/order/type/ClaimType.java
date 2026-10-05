package showroomz.domain.order.type;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/** 클레임 유형(35 설계서 1-1) — 한 번의 신청은 한 유형이다. */
@Getter
@RequiredArgsConstructor
public enum ClaimType {
    RETURN("반품"),
    EXCHANGE("교환");

    private final String label;
}
