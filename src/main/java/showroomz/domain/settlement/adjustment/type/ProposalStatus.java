package showroomz.domain.settlement.adjustment.type;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * 제안 상태(44 이슈 스레드 설계서 1-4). {@code REJECTED}는 최신 제안인 동안만 되살아난다(반대한 쪽의 동의 · 누구든 다른 금액 제안).
 * {@code CLOSED}는 응답 없이 협의가 끝났다 — 기한 만료 또는 요청자 철회(1021 기-14). 둘은 같은 {@code closeOpen}으로 닫혀 제안 행만으로는
 * 구분되지 않는다 — 카드 문구는 협의 상태로 고른다({@link #closedLabel}).
 */
@Getter
@RequiredArgsConstructor
public enum ProposalStatus {
    PENDING("응답 대기"),
    ACCEPTED("동의"),
    REJECTED("반대"),
    COUNTERED("다른 금액 제안으로 응답됨"),
    CLOSED("기한 만료");

    /** 철회로 닫힌 제안 카드의 결과 문구(55 계획서 S4 · D10). */
    public static final String WITHDRAWN_LABEL = "요청 철회";

    private final String label;

    /**
     * 닫힌 제안 카드의 결과 문구 — 협의가 철회로 끝났으면 「요청 철회」, 그 밖(기한 만료 · 협의 행 없음)은 「기한 만료」(55 계획서 S4).
     * 셀러 · 스튜디오 · 어드민 카드가 같은 판정을 쓴다.
     */
    public static String closedLabel(AdjustmentStatus adjustmentStatus) {
        return adjustmentStatus == AdjustmentStatus.WITHDRAWN ? WITHDRAWN_LABEL : CLOSED.label;
    }
}
