package showroomz.domain.post.type;

import java.util.List;

/**
 * 게시물의 <b>노출 상태</b> (§24-1).
 *
 * <p>이 enum은 노출 축만 담당한다. 공구의 D-3·품절·공구 마감은 상품·공구 데이터에서 파생되는
 * 값이지 게시물의 상태가 아니다(C5: 일부만 품절이면 배지는 D-3 그대로). 섞으면 공구 게시물이
 * 들어오는 순간 값이 열두 종으로 폭발한다.
 *
 * <p>화면 배지는 3종(작성중·게시중·노출 중지)이지만 서버 상태는 5종이다. 나머지 둘은
 * {@link #UNDER_REVIEW}(배지는 여전히 "노출 중지" — 사실이 바뀐 게 아니다)와
 * {@link #DELETED}(목록에서 제외)다.
 */
public enum PostStatus {

    /** 작성중(임시저장) — 쇼룸에 노출되지 않는다. 화면 배지는 정보색 */
    DRAFT,

    /** 게시중 — 쇼룸 노출 중. 화면 배지는 성공색 */
    PUBLISHED,

    /** 노출 중지 — 운영자 조치. 이의 신청 기간이 함께 시작된다. 화면 배지는 위험색 */
    SUSPENDED,

    /**
     * 이의 심사 중.
     *
     * <p>{@link #SUSPENDED}와 합치지 않고 별도 값으로 두는 이유 — <b>이 상태에서만 삭제가 금지</b>된다
     * (§24-5: 신청 후 도중에 지우면 처리 결과가 붕 뜬다). 합치면 삭제 가능 여부를 매번
     * 이의신청 테이블 조인으로 판정해야 한다.
     */
    UNDER_REVIEW,

    /** 영구 삭제 — 인플루언서 기준의 삭제다. 서버는 비공개로 보관하고 운영자 콘솔에서만 조회된다 (§24-6) */
    DELETED;

    /** 소비자에게 보이는 상태는 게시중 하나뿐이다. */
    public boolean isVisibleToConsumer() {
        return this == PUBLISHED;
    }

    /** 수정 허용 — 중지·심사 중에는 심사 대상이 도중에 바뀌면 안 된다 (§24-5) */
    public boolean isEditable() {
        return this == DRAFT || this == PUBLISHED;
    }

    /** 삭제 허용 — 중지 중 본인 삭제는 허용하되(출구), 심사 중에는 막는다 (§24-5) */
    public boolean isDeletable() {
        return this == DRAFT || this == PUBLISHED || this == SUSPENDED;
    }

    /**
     * 이 상태 탭이 실제로 담는 상태들 (§24-1).
     *
     * <p>화면 배지는 3종인데 서버 상태는 5종이라 탭 하나가 상태 둘을 담는 경우가 생긴다 —
     * 「노출 중지」가 그렇다. 심사 중은 배지도 탭도 여전히 노출 중지다(§24-5: 사실이 바뀐 게 아니다).
     *
     * <p><b>탭 개수와 탭 목록이 같은 답을 써야 한다.</b> 한쪽만 심사 중을 포함하면, 이의 신청을
     * 넣은 순간 게시물이 자기 탭 목록에서 사라지면서 숫자만 남는다 — 인플루언서에게는 글이
     * 증발한 것으로 보인다.
     */
    public List<PostStatus> tabMembers() {
        return this == SUSPENDED ? List.of(SUSPENDED, UNDER_REVIEW) : List.of(this);
    }
}
