package showroomz.api.seller.basicinfo;

/**
 * 계좌번호 마스킹(§16-7) — 뒤 6자리를 남기고 앞을 자릿수만큼 {@code *}로 치환한다.
 * 파트너센터(seller) 응답에만 적용한다. 어드민 응답은 전체 노출이 목적(통장 사본 대조)이므로
 * 어드민 패키지에는 이 클래스를 절대 들여오지 않는다 — 두 화면의 마스킹을 공용 유틸로 묶지 않는다(§16-7).
 */
public final class SettlementAccountMasker {

    private static final int VISIBLE_SUFFIX_LENGTH = 6;

    private SettlementAccountMasker() {
    }

    public static String mask(String accountNumber) {
        if (accountNumber == null) {
            return null;
        }
        if (accountNumber.length() <= VISIBLE_SUFFIX_LENGTH) {
            return "*".repeat(accountNumber.length());
        }
        int maskedLength = accountNumber.length() - VISIBLE_SUFFIX_LENGTH;
        return "*".repeat(maskedLength) + accountNumber.substring(maskedLength);
    }
}
