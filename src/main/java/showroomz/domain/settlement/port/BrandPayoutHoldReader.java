package showroomz.domain.settlement.port;

/**
 * 브랜드 몫 지급 정지 여부(§51-3 · 52 설계서 6-3) — 정산이 회원을 <b>읽는</b> 방향의 포트다. 구현은 회원 모듈
 * ({@code BrandSanctionReader} — 열린 정지 ∧ 지급 정지 사유).
 *
 * <p>확정(브랜드 행을 SCHEDULED 대신 BLOCKED 로)과 지급 배치 앞단(정지 직후 · 확정과의 경합 틈새)이 같은 판정을 쓴다.
 */
public interface BrandPayoutHoldReader {

    boolean isHeld(Long marketId);
}
