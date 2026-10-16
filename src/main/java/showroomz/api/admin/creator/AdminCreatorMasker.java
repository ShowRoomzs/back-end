package showroomz.api.admin.creator;

import showroomz.api.admin.user.AdminUserMasker;
import showroomz.global.utils.MaskingUtils;

/**
 * 인플루언서 상세의 마스킹(§53-2 · 53 설계서 2-2) — <b>서버에서 끝낸다.</b> 원본을 내려보내고 화면이 가리면 응답 페이로드에 전체 값이
 * 이미 있어 열람 통제가 성립하지 않는다.
 *
 * <ul>
 *   <li>이름 · 예금주 — 가운데(김*연) · 소비자 목록과 같은 규칙({@link AdminUserMasker#maskName})</li>
 *   <li>생년월일 — <b>연도만</b>(1994.**.**) · C15 규칙({@link MaskingUtils#maskBirthday} · 1994.06.**)과 다르다</li>
 *   <li>휴대폰 — 가운데 4자리(010-****-8842)</li>
 *   <li>계좌번호 — 앞 3 · 뒤 4 자리만 · 구분자 보존(3333-**-**8842) · <b>해제 경로가 없다</b>(전체 값은 정산 관리에서만)</li>
 * </ul>
 */
public final class AdminCreatorMasker {

    private AdminCreatorMasker() {
    }

    public static String maskName(String name) {
        return AdminUserMasker.maskName(name);
    }

    public static String maskPhone(String phoneNumber) {
        return MaskingUtils.maskPhoneNumber(phoneNumber);
    }

    /** 1994-06-02 → 1994.**.** · 값이 없거나 형식이 다르면 null. */
    public static String maskBirthdayYearOnly(String birthday) {
        String digits = digits(birthday);
        return digits == null || digits.length() < 4 ? null : digits.substring(0, 4) + ".**.**";
    }

    /** 1994-06-02 → 1994.06.02 · 해제 상태의 표기. */
    public static String formatBirthday(String birthday) {
        String digits = digits(birthday);
        if (digits == null || digits.length() < 8) {
            return birthday;
        }
        return digits.substring(0, 4) + "." + digits.substring(4, 6) + "." + digits.substring(6, 8);
    }

    /** 01033918842 → 010-3391-8842 · 해제 상태의 표기. 자릿수가 다르면 원문. */
    public static String formatPhone(String phoneNumber) {
        String digits = digits(phoneNumber);
        if (digits == null) {
            return null;
        }
        if (digits.length() == 11) {
            return digits.substring(0, 3) + "-" + digits.substring(3, 7) + "-" + digits.substring(7);
        }
        if (digits.length() == 10) {
            return digits.substring(0, 3) + "-" + digits.substring(3, 6) + "-" + digits.substring(6);
        }
        return phoneNumber;
    }

    /** 3333-01-128842 → 333*-**-**8842 · 숫자 앞 3 · 뒤 4 자리만 남긴다. 7자리 이하면 뒤 4자리만. */
    public static String maskAccountNumber(String accountNumber) {
        if (accountNumber == null || accountNumber.isBlank()) {
            return null;
        }
        String trimmed = accountNumber.trim();
        long total = trimmed.chars().filter(Character::isDigit).count();
        int head = total > 7 ? 3 : 0;
        long tailStart = total - 4;
        StringBuilder masked = new StringBuilder(trimmed.length());
        int index = 0;
        for (char ch : trimmed.toCharArray()) {
            if (!Character.isDigit(ch)) {
                masked.append(ch);
                continue;
            }
            masked.append(index < head || index >= tailStart ? ch : '*');
            index++;
        }
        return masked.toString();
    }

    private static String digits(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.replaceAll("[^0-9]", "");
    }
}
