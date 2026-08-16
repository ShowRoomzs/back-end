package showroomz.domain.post.type;

/**
 * 게시물 타입 판별자 (§24 비교표).
 *
 * <p>지금 만드는 것은 {@link #GENERAL} 하나뿐이다. 그럼에도 판별자를 먼저 두는 이유는,
 * 공구 게시물이 들어올 때 <b>스키마를 두 번 갈아엎지 않기 위해서</b>다. 공구는
 * {@code group_buy_post} 1:1 확장 테이블로 붙고, 좋아요·제재·이의신청·노출·알림 같은
 * 횡단 테이블은 {@code post_id} 하나로 두 타입에 그대로 걸린다.
 *
 * <p>타입별로 갈리는 규칙은 서비스의 {@code if} 분기가 아니라
 * {@link showroomz.domain.post.policy.PostPolicy} 구현체로 나뉜다.
 */
public enum PostType {

    /** 일반 게시물 — 제목 없음 · 사진 최대 20장 · 상시 수정 가능 (§24) */
    GENERAL,

    /** 공구 게시물 — 제목 필수 · 본문 글만 · 노출중 잠금. 아직 만들지 않는다 (§24 비교표) */
    GROUP_BUY
}
