package showroomz.domain.order.type;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * 클레임을 연 쪽(1009 기획 수정본 8-1 B6) — 구매확정 뒤 하자는 소비자가 직접 반품을 신청하지 못하고 1:1 문의 → 운영자가 대신 연다.
 * 연 뒤에는 일반 반품과 같은 흐름이다(회수 송장은 소비자 · 검수는 브랜드 · 통과 시 PG 자동 환불).
 */
@Getter
@RequiredArgsConstructor
public enum ClaimOpenedBy {
    CONSUMER("소비자 신청"),
    OPERATOR("운영자 개설");

    private final String label;
}
