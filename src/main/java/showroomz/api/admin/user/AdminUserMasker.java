package showroomz.api.admin.user;

import showroomz.global.utils.MaskingUtils;

/**
 * 소비자 목록의 이름 마스킹 (§25-1) — <b>가운데 1자</b>를 가린다(홍길동 → 홍*동).
 *
 * <p>{@link MaskingUtils#maskName(String)}과 합치지 않는다. 그쪽은 C15 내 정보 화면의 규칙이라
 * <b>끝 글자</b>를 가린다(김수민 → 김수*). 규칙이 다른 마스킹을 한 메서드로 묶으면 어느 화면에서
 * 어떤 값이 나가는지 호출부만 봐서는 알 수 없게 된다.
 *
 * <p>휴대폰은 반대로 규칙이 같아(가운데 4자리 · 010-****-1234) {@link MaskingUtils}를 그대로 쓴다.
 * 같은 규칙을 두 벌 두면 한쪽만 고쳐지는 날이 온다.
 *
 * <p>마스킹은 <b>서버에서 끝낸다.</b> 원본을 내려보내고 화면이 가리는 방식이면 응답 페이로드에
 * 이미 전체 값이 들어 있어 열람 통제가 성립하지 않는다. 목록에는 해제 경로가 없으므로(§25-1)
 * 원본을 아예 내보내지 않는다.
 */
public final class AdminUserMasker {

    private AdminUserMasker() {
    }

    /**
     * 홍길동 → 홍*동 · 김민 → 김* · 남궁민수 → 남**수. 값이 없으면 null.
     *
     * <p>네 글자 이상은 가운데를 <b>전부</b> 가린다. "가운데 1자"는 세 글자 이름을 전제한 표현이고,
     * 글자 수가 늘 때 노출을 늘리는 쪽으로 반올림할 이유가 없다.
     */
    public static String maskName(String name) {
        if (name == null || name.isBlank()) {
            return null;
        }
        String trimmed = name.trim();
        if (trimmed.length() == 1) {
            return "*";
        }
        if (trimmed.length() == 2) {
            return trimmed.charAt(0) + "*";
        }
        return trimmed.charAt(0)
                + "*".repeat(trimmed.length() - 2)
                + trimmed.charAt(trimmed.length() - 1);
    }

    /** 01012341234 → 010-****-1234. 값이 없거나 자릿수가 모자라면 null */
    public static String maskPhoneNumber(String phoneNumber) {
        return MaskingUtils.maskPhoneNumber(phoneNumber);
    }
}
