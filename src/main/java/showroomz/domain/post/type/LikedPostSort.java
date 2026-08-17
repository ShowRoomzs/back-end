package showroomz.domain.post.type;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 좋아요 목록 정렬 기준 (C3 좋아요 화면의 정렬 바텀시트).
 *
 * <p>C2 팔로잉의 정렬과 같은 규격이지만 <b>정렬을 DB에서 끝낸다</b>는 점이 다르다. 팔로잉은
 * 한 사용자가 팔로우한 쇼룸을 전부 메모리에 올려도 되는 크기지만, 좋아요는 게시물 단위라
 * 상한이 없다 — 전부 읽어와 자바에서 정렬하면 오래 쓴 계정에서 그대로 무너진다.
 *
 * <p>정렬 키는 두 테이블에 나뉘어 있다. <b>좋아요한 시각</b>은 {@code post_like.created_at}
 * (내가 언제 눌렀나), <b>좋아요 수</b>는 {@code post.like_count}(남들이 몇 번 눌렀나)다.
 * 같은 "좋아요"라는 말을 쓰지만 서로 다른 테이블의 다른 값이다.
 */
@Getter
@AllArgsConstructor
public enum LikedPostSort {

    /** 기본 — 최근에 좋아요한 순서 */
    DEFAULT("기본"),

    /** 좋아요한 날짜: 오래된순 — 가장 먼저 좋아요한 게시물부터 */
    LIKED_OLDEST("좋아요한 날짜 : 오래된순"),

    /** 좋아요 많은순 — 많은 사람이 좋아한 게시물부터 */
    MOST_LIKED("좋아요 많은순"),

    /**
     * 공구 게시물 먼저 — 공구를 위로 모아서.
     *
     * <p>지금은 {@link PostType#GROUP_BUY} 게시물이 존재하지 않아 결과가 {@link #DEFAULT}와 같다.
     * 그래도 값을 먼저 두는 이유는, 공구 게시물이 들어올 때 <b>앱이 쓰는 정렬 계약을 다시 바꾸지
     * 않기 위해서</b>다. 진행 중/종료를 가르는 정렬은 공구 확장 테이블이 생긴 뒤에야 가능하다.
     */
    GROUP_BUY_FIRST("공구 게시물 먼저");

    private final String description;
}
