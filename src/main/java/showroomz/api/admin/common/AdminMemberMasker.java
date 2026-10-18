package showroomz.api.admin.common;

import showroomz.global.utils.MaskingUtils;

/**
 * 어드민 회원 관리의 마스킹 — 소비자(§25-1) · 브랜드(§52-1) · 인플루언서(§53-2) 세 화면이 <b>한 클래스</b>를 쓴다(정합 계획서 R0-3 ·
 * R1-2). 같은 규칙을 화면마다 따로 구현하면 한쪽만 고쳐진다 — 계좌 마스킹이 브랜드 · 인플루언서에서 7자리 이하일 때 달랐던 것이 그 예다.
 *
 * <p>마스킹은 <b>서버에서 끝낸다.</b> 원본을 내려보내고 화면이 가리면 응답 페이로드에 전체 값이 이미 있어 열람 통제가 성립하지 않는다.
 *
 * <ul>
 *   <li>이름 · 예금주 — <b>가운데</b>(홍*동 · 남**수). C15 내 정보 화면({@link MaskingUtils#maskName} · 끝 글자)과 다르다</li>
 *   <li>휴대폰 — 가운데 4자리(010-****-8842) · C15 와 같은 규칙이라 {@link MaskingUtils}에 위임</li>
 *   <li>생년월일 — <b>연도만</b>(1994.**.**) · C15(1994.06.**)와 다르다</li>
 *   <li>계좌번호 — 숫자 앞 3 · 뒤 4 자리만 · 구분자 보존(3333-01-128842 → 333*-**-**8842). 숫자 7자리 이하면 뒤 4자리만.
 *   파트너 · 스튜디오용 {@code SettlementAccountMasker}(뒤 6자리)와 규칙이 다르다 — 회원 관리의 목적은 신원 대조이고 송금은 정산 관리의 일이다</li>
 * </ul>
 *
 * <p>메서드 이름이 규칙을 말한다 — 화면별 규칙이 다른 값(생년월일)은 메서드를 나눠 둔다.
 */
public final class AdminMemberMasker {

    private static final int ACCOUNT_HEAD = 3;
    private static final int ACCOUNT_TAIL = 4;

    private AdminMemberMasker() {
    }

    /** 홍길동 → 홍*동 · 김민 → 김* · 남궁민수 → 남**수 · 값이 없으면 null. */
    public static String maskName(String name) {
        return MaskingUtils.maskNameMiddle(name);
    }

    /** 01012341234 · 010-1234-1234 → 010-****-1234 · 값이 없거나 자릿수가 모자라면 null. */
    public static String maskPhone(String phoneNumber) {
        return MaskingUtils.maskPhoneNumber(phoneNumber);
    }

    /** 1994-06-02 → 1994.**.** · 값이 없거나 형식이 다르면 null. */
    public static String maskBirthdayYearOnly(String birthday) {
        String digits = digits(birthday);
        return digits == null || digits.length() < 4 ? null : digits.substring(0, 4) + ".**.**";
    }

    /** 1994-06-02 → 1994.06.02 · 마스킹 해제 상태의 표기. 자릿수가 다르면 원문. */
    public static String formatBirthday(String birthday) {
        String digits = digits(birthday);
        if (digits == null || digits.length() < 8) {
            return birthday;
        }
        return digits.substring(0, 4) + "." + digits.substring(4, 6) + "." + digits.substring(6, 8);
    }

    /** 01033918842 → 010-3391-8842 · 마스킹 해제 상태의 표기. 자릿수가 다르면 원문. */
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

    /**
     * 3333-01-128842 → 333*-**-**8842 · 110123456789 → 110*****6789. 숫자 앞 3 · 뒤 4 자리만 남기고 구분자는 그대로 둔다. 숫자가 7자리
     * 이하면 앞자리를 남길 여유가 없어 뒤 4자리만 남긴다(1234567 → ***4567). 값이 없으면 null.
     */
    public static String maskAccountNumber(String accountNumber) {
        if (accountNumber == null || accountNumber.isBlank()) {
            return null;
        }
        String trimmed = accountNumber.trim();
        long total = trimmed.chars().filter(Character::isDigit).count();
        int head = total > ACCOUNT_HEAD + ACCOUNT_TAIL ? ACCOUNT_HEAD : 0;
        long tailStart = total - ACCOUNT_TAIL;
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
