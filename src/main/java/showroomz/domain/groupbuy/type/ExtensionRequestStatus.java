package showroomz.domain.groupbuy.type;

/**
 * 연장 요청 결과. 거절 2경로(명시 거절 · 기간 만료)를 값으로 나눈다 — 결과는 같아도 B4e 화면이
 * 처리자를 다르게 적고, 분쟁 시 「거절당했다」와 「응답이 없었다」는 다른 사실이다(설계서 1-5).
 */
public enum ExtensionRequestStatus {
    PENDING, ACCEPTED, REJECTED, EXPIRED
}
