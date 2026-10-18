package showroomz.domain.member.user.type;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * 정지 중 서버가 거절하는 소비자 행위(§51-1 · 51-6 설계서 4절) — 409 메시지에 「무엇이 막혔는지」를 싣는다. 말투는 시안 안내 줄
 * (「이용 정지 중에는 구매할 수 없어요」)과 같다.
 */
@Getter
@RequiredArgsConstructor
public enum UserBlockedAction {
    CART_ADD("장바구니에 담을"),
    PURCHASE("구매할"),
    PRODUCT_INQUIRY("문의할"),
    FOLLOW("팔로우할"),
    LIKE("좋아요를 누를"),
    POST_REPORT("게시물을 신고할"),
    PROFILE_UPDATE("닉네임 · 프로필 사진을 바꿀");

    /** 「이용 정지 중에는 {phrase} 수 없어요.」의 가운데. */
    private final String phrase;

    public String message() {
        return "이용 정지 중에는 " + phrase + " 수 없어요.";
    }
}
