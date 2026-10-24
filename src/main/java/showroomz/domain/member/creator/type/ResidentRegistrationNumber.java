package showroomz.domain.member.creator.type;

import java.util.regex.Pattern;

/**
 * 주민등록번호 형식 검사 · 마스킹(1009 기획 수정본 7-a). 체크섬은 검사하지 않는다 — 2020.10 이후 발급분은 뒷자리가
 * 임의 번호라 체크섬이 맞지 않는다. 앞 6자리는 실제 날짜여야 하고, 뒷자리 첫 숫자는 성별·세기 구분(1~8)이다.
 */
public final class ResidentRegistrationNumber {

    private static final Pattern DIGITS = Pattern.compile("^\\d{13}$");

    private ResidentRegistrationNumber() {
    }

    /** 하이픈 · 공백을 지운 13자리 숫자. 형식이 맞지 않으면 null. */
    public static String normalize(String raw) {
        if (raw == null) {
            return null;
        }
        String digits = raw.replaceAll("[\\s-]", "");
        if (!DIGITS.matcher(digits).matches()) {
            return null;
        }
        int month = Integer.parseInt(digits.substring(2, 4));
        int day = Integer.parseInt(digits.substring(4, 6));
        char gender = digits.charAt(6);
        if (month < 1 || month > 12 || day < 1 || day > 31 || gender < '1' || gender > '8') {
            return null;
        }
        return digits;
    }

    private static final String MASKED_BACK = "*******";

    /**
     * {@code 900101-*******} — 화면 표시용. 원문 대신 이 값을 저장해 두고 읽는다. 뒤 7자리는 <b>성별 자리까지</b> 가린다 —
     * 온보딩 입력(뒤 7자리 마스킹)과 표기를 맞춘다(스튜디오 06 「온보딩 rev.3 정합」).
     */
    public static String mask(String normalized) {
        return normalized.substring(0, 6) + "-" + MASKED_BACK;
    }

    /**
     * 저장된 마스킹 값을 화면 표기로 — 이전 규칙({@code 900101-1******})으로 저장된 행도 성별 자리를 가려 내린다.
     * 값이 없거나 형식이 다르면 그대로.
     */
    public static String display(String storedMask) {
        if (storedMask == null || storedMask.length() < 7 || storedMask.charAt(6) != '-') {
            return storedMask;
        }
        return storedMask.substring(0, 7) + MASKED_BACK;
    }
}
