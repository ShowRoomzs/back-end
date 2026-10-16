package showroomz.domain.settlement.port;

/**
 * 인플루언서 몫(리워드) 지급 정지 여부(§51-3 · 53 설계서 4-3) — 정산이 회원을 <b>읽는</b> 방향의 포트다. 구현은 회원 모듈
 * ({@code CreatorSanctionReader} — 열린 정지 ∧ 지급 정지 사유). 브랜드의 {@link BrandPayoutHoldReader}와 짝이다.
 *
 * <p>{@code PayoutBlockPolicy}(확정 · 보류 재판정 · 어드민 표시)와 지급 배치 앞단 · 세금계산서 확인이 같은 판정을 쓴다.
 */
public interface CreatorPayoutHoldReader {

    boolean isHeld(Long creatorId);
}
