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
     * 공구 게시물 먼저 — <b>진행 중인</b> 공구를 위로 모아서(공구 게시물 설계 6-1).
     *
     * <p>진행 중 공구({@link PostType#GROUP_BUY} ∧ 판매 상태 ∧ 종료 시각 전)가 먼저 오고, 그 안에서는 최근에 좋아요한
     * 순서다. 종료 3일 이내의 마감 게시물은 앞으로 모으지 않는다 — 일반 게시물과 같은 줄에서 좋아요한 시각으로 섞인다.
     */
    GROUP_BUY_FIRST("공구 게시물 먼저");

    private final String description;
}
