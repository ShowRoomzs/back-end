package showroomz.global.utils;

/**
 * 계좌번호 마스킹(§16-7) — 뒤 6자리를 남기고 앞을 자릿수만큼 {@code *}로 치환한다.
 * 파트너센터(기본정보 · 정산 관리) · 쇼룸 스튜디오(정산 관리) 응답에만 적용한다 — 44 정산 설계서 공통 결정 #14 로 두 수취자 화면이
 * 같은 형식을 쓰려고 {@code global.utils}로 옮겼다. 어드민 응답은 전체 노출이 목적(통장 사본 대조)이므로
 * 어드민 패키지에는 이 클래스를 절대 들여오지 않는다(§16-7).
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
