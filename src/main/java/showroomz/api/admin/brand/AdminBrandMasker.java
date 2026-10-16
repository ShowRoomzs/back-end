package showroomz.api.admin.brand;

/**
 * 어드민 브랜드 상세의 계좌번호 마스킹(§52-1 · 52 설계서 0-8) — <b>가운데</b>를 가린다: 앞 3자리 · 뒤 4자리 숫자만 남기고 구분자는 보존한다
 * ({@code 110-1234-3841} → {@code 110-****-3841}). 회원 관리의 목적은 신원 확인이고 송금은 정산 관리의 일이다 — 이 화면에 해제가 없으므로
 * 원본은 응답에 싣지 않는다. 파트너용 {@code SettlementAccountMasker}(뒤 6자리)와 규칙이 달라 따로 둔다.
 */
public final class AdminBrandMasker {

    private static final int HEAD = 3;
    private static final int TAIL = 4;

    private AdminBrandMasker() {
    }

    public static String maskAccountNumber(String accountNumber) {
        if (accountNumber == null || accountNumber.isBlank()) {
            return null;
        }
        long digits = accountNumber.chars().filter(Character::isDigit).count();
        if (digits <= HEAD + TAIL) {
            return accountNumber.replaceAll("\\d", "*");
        }
        StringBuilder out = new StringBuilder(accountNumber.length());
        int seen = 0;
        for (char c : accountNumber.toCharArray()) {
            if (!Character.isDigit(c)) {
                out.append(c);
                continue;
            }
            out.append(seen < HEAD || seen >= digits - TAIL ? c : '*');
            seen++;
        }
        return out.toString();
    }
}
