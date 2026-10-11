package showroomz.domain.settlement.adjustment.type;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * 제안 상태(44 이슈 스레드 설계서 1-4). {@code REJECTED}는 최신 제안인 동안만 되살아난다(반대한 쪽의 동의 · 누구든 다른 금액 제안).
 * {@code CLOSED}는 응답 없이 협의가 기한 만료로 끝났다.
 */
@Getter
@RequiredArgsConstructor
public enum ProposalStatus {
    PENDING("응답 대기"),
    ACCEPTED("동의"),
    REJECTED("반대"),
    COUNTERED("다른 금액 제안으로 응답됨"),
    CLOSED("기한 만료");

    private final String label;
}
