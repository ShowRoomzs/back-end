package showroomz.global.utils;

import java.util.regex.Pattern;

/**
 * §22-1 쇼룸명 규칙 — 2~20자, 한글·영문·숫자·공백만(특수문자 불가), 중복 불가.
 *
 * <p>가입 온보딩과 쇼룸 관리(#8)가 <b>같은 규칙</b>을 써야 한다. 두 곳이 갈라지면 가입 때 통과한
 * 이름이 수정 화면에서 거부되는(혹은 그 반대의) 상황이 생기므로 규칙을 한곳에 둔다.
 */
public final class ShowroomNamePolicy {

    public static final Pattern PATTERN = Pattern.compile("^[가-힣a-zA-Z0-9 ]{2,20}$");
    public static final String FORMAT_MESSAGE = "쇼룸명은 2~20자, 한글·영문·숫자·공백만 사용할 수 있습니다.";

    private ShowroomNamePolicy() {
    }

    public static boolean isValidFormat(String showroomName) {
        return showroomName != null && PATTERN.matcher(showroomName).matches();
    }
}
