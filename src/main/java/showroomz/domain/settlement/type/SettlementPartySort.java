package showroomz.domain.settlement.type;

/**
 * 파트너 · 스튜디오 정산 목록 정렬(파트너 설계서 2-2 · 스튜디오 설계서 2-2). {@code PAYOUT_DESC}는 서피스의 「내 몫」 — 파트너는 브랜드
 * 수취액, 스튜디오는 인플루언서 실지급액 높은순 · 같으면 생성일 최신순.
 */
public enum SettlementPartySort {
    CREATED_DESC,
    PAYOUT_DESC
}
