package showroomz.domain.post.policy;

import showroomz.domain.post.entity.Post;
import showroomz.domain.post.type.PostType;

/**
 * 타입별로 갈리는 게시물 규칙.
 *
 * <p>구현체가 지금은 {@link GeneralPostPolicy} 하나뿐이다. 그런데도 인터페이스를 먼저 두는 이유는,
 * 여기 모인 네 가지가 공구 게시물이 들어오는 순간 <b>반드시</b> 갈리기 때문이다 —
 * 제목 필수 여부 · 사진 허용 여부 · 수정 가능 시점 · 대가관계 표시(§24 비교표).
 *
 * <p>나중에 도입하면 그때는 이미 서비스 곳곳에 퍼진 {@code if (postType == GROUP_BUY)} 분기를
 * 걷어내는 일부터 해야 한다. 분기가 생기기 전에 자리를 만들어 두는 편이 싸다.
 */
public interface PostPolicy {

    PostType supports();

    /** 게시 가능한 상태인지 — 일반은 사진 최소 1장(§24-3), 공구는 상품 연결이 조건이 된다 */
    void validateForPublish(Post post);

    /** 수정 가능한 시점인지 — 일반은 상시 허용, 공구는 노출중 잠금(§24 비교표) */
    void validateEditable(Post post);

    /** 좋아요를 받을 수 있는지 — 공구는 마감이면 막고, 품절은 허용한다(C5) */
    boolean canLike(Post post);

    /** 대가관계(유료 광고 포함) 표시가 자동 삽입되는지 — 일반 게시물은 광고가 아니므로 없다 */
    boolean requiresAdDisclosure();
}
