package showroomz.domain.settlement.port;

import showroomz.domain.settlement.type.SettlementPayee;

/**
 * 수취자 몫 지급 정지 여부(§51-3 · 52 설계서 6-3 · 53 설계서 4-3) — 정산이 회원을 <b>읽는</b> 방향의 포트 하나다(정합 계획서 R1-5). 구현은
 * 회원 모듈({@code MemberPayoutHoldReader} — 열린 정지 ∧ 지급 정지 사유 스냅샷).
 *
 * <p>확정 · 보류 재판정({@code PayoutBlockPolicy}) · 지급 지시 앞단 · 세금계산서 확인이 같은 판정을 쓴다.
 */
public interface PayoutHoldReader {

    /**
     * @param payee 수취자 — 브랜드 · 인플루언서만 정지가 있다. 플랫폼은 항상 false
     * @param refId 브랜드 = {@code market_id} · 인플루언서 = {@code creator_id}
     */
    boolean isHeld(SettlementPayee payee, Long refId);
}
