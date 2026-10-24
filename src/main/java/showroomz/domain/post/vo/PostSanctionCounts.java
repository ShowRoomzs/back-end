package showroomz.domain.post.vo;

/**
 * 작성자(인플루언서) 조치 이력 — <b>횟수만</b> (§54-3 레일 「노출 중지 N회 · 삭제 N회」).
 *
 * <p>사실 기록이고 판단 지표로 가공하지 않는다. 어드민 일반 게시물 상세와 인플루언서 상세가 같은 값을 쓴다
 * — 두 화면이 다른 숫자를 내면 안 된다.
 *
 * @param suspensionCount     노출 중지 횟수 — 운영자가 스스로 거둔 조치(조치 취소)는 빼고 센다
 * @param sanctionDeleteCount 제재로 끝난 삭제 횟수 — 이의 반려 + 기한 경과. 작성자 본인 삭제는 세지 않는다
 */
public record PostSanctionCounts(long suspensionCount, long sanctionDeleteCount) {

    public static final PostSanctionCounts NONE = new PostSanctionCounts(0, 0);
}
