package showroomz.global.utils;

/**
 * 작성자 닉네임 마스킹 (§23-3) — 파트너센터는 첫 글자만 남기고 가린 닉네임만 본다(구****).
 * 브랜드는 실명·연락처를 볼 수 없고 회원 상세 링크도 없다.
 */
public final class NicknameMasker {

    private static final String MASK = "****";
    private static final String UNKNOWN = "알 수 없음";

    private NicknameMasker() {
    }

    public static String mask(String nickname) {
        if (nickname == null || nickname.isBlank()) {
            return UNKNOWN;
        }
        return nickname.trim().substring(0, 1) + MASK;
    }
}
