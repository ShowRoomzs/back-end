package showroomz.domain.groupbuy.type;

/**
 * 이행 확인을 제출한 측. <b>확인 대상은 상대의 의무다</b> — SELLER 행은
 * 「인플루언서의 콘텐츠 의무를 확인했다」는 뜻이다(§29-10 · 설계서 4-6).
 */
public enum FulfillmentSide {
    SELLER, CREATOR;

    public FulfillmentSide counterpart() {
        return this == SELLER ? CREATOR : SELLER;
    }
}
