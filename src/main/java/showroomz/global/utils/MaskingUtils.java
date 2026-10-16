package showroomz.global.utils;

/**
 * C15-2 회원정보 마스킹 — 이름·생년월일·휴대폰번호는 본인인증(PASS) 결과라 조회 화면에 가려서 보여준다.
 * 원본을 내려보내고 클라이언트가 가리는 방식이면 가린 의미가 없으므로 서버에서 끝낸다.
 */
public final class MaskingUtils {

    private MaskingUtils() {
    }

    /** 김수민 -> 김수*, 김수 -> 김*, 값이 없으면 null */
    public static String maskName(String name) {
        if (name == null || name.isBlank()) {
            return null;
        }
        String trimmed = name.trim();
        if (trimmed.length() == 1) {
            return "*";
        }
        return trimmed.substring(0, trimmed.length() - 1) + "*";
    }

    /**
     * 가운데를 가린다 — 신고자 표기(§54-3 「한*준」). 신고 대상에게 신고자가 드러나지 않게 어드민에서도 가린다.
     *
     * <p>{@link #maskName}(끝 글자를 가린다 · C15-2 회원정보)과 규칙이 다르다: 한준 -> 한*, 한지준 -> 한*준,
     * 박다은이 -> 박**이, 한 글자 -> *, 값이 없으면 null.
     */
    public static String maskNameMiddle(String name) {
        if (name == null || name.isBlank()) {
            return null;
        }
        String trimmed = name.trim();
        int length = trimmed.length();
        if (length == 1) {
            return "*";
        }
        if (length == 2) {
            return trimmed.charAt(0) + "*";
        }
        return trimmed.charAt(0) + "*".repeat(length - 2) + trimmed.charAt(length - 1);
    }

    /** 1998-04-12 -> 1998.04.**, 값이 없거나 형식이 다르면 null */
    public static String maskBirthday(String birthday) {
        if (birthday == null || birthday.isBlank()) {
            return null;
        }
        String digits = birthday.replaceAll("[^0-9]", "");
        if (digits.length() < 6) {
            return null;
        }
        return digits.substring(0, 4) + "." + digits.substring(4, 6) + ".**";
    }

    /** 01012341234 / 010-1234-1234 -> 010-****-1234, 값이 없거나 자릿수가 모자라면 null */
    public static String maskPhoneNumber(String phoneNumber) {
        if (phoneNumber == null || phoneNumber.isBlank()) {
            return null;
        }
        String digits = phoneNumber.replaceAll("[^0-9]", "");
        if (digits.length() < 9) {
            return null;
        }
        return digits.substring(0, 3) + "-****-" + digits.substring(digits.length() - 4);
    }
}
