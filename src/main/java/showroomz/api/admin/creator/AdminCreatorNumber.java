package showroomz.api.admin.creator;

/**
 * 인플루언서 회원번호 {@code INF-3018}(53 설계서 0-12) — 별도 채번 없이 {@code creator_id}를 포맷한다. 소통 스레드의
 * {@code AdminChannelMemberNumber}(36 설계 잠정 규칙)와 같은 값이다 — 두 화면이 같은 번호를 보여야 CS 가 대조할 수 있다.
 */
public final class AdminCreatorNumber {

    private static final String PREFIX = "INF-";

    private AdminCreatorNumber() {
    }

    public static String format(Long creatorId) {
        return creatorId == null ? null : PREFIX + creatorId;
    }
}
