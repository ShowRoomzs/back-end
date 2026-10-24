package showroomz.domain.member.brand.type;

/**
 * 탈퇴 조건 행 · 내역 행의 이동 대상(51 설계서 4절) — FE 라우트는 FE 가 만든다. 앞 넷은 조건 행, 뒤 둘은 지급 보류 내역 행(4-G2 「정산 정보
 * 수정하기 ↗」 · 사업자 정보 확인)이 쓴다.
 */
public enum BrandWithdrawalTarget {
    GROUP_BUYS,
    CONTRACTS,
    ORDERS,
    SETTLEMENTS,
    SETTLEMENT_ACCOUNT,
    BUSINESS_INFO
}
