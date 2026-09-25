package showroomz.domain.groupbuy.type;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import showroomz.domain.groupbuy.entity.GroupBuy;
import showroomz.domain.groupbuy.entity.GroupBuyPost;

/**
 * 공구 게시물 8종 — <b>저장하지 않고 파생한다</b>(설계서 1-8).
 *
 * <p>게시물은 공구를 따라가는 값이지 공구를 움직이는 값이 아니다(§29-3). 따라가는 값을 따로 저장하면
 * 스케줄러가 공구를 열고 게시물 갱신을 빠뜨린 순간 두 열이 어긋난다. 3서피스가 {@link #of} 하나만 호출한다.
 */
@Getter
@RequiredArgsConstructor
public enum GroupBuyPostStatus {

    NOT_WRITTEN("미작성", GroupBuyTone.NEUTRAL),
    WRITING("작성중", GroupBuyTone.NEUTRAL),
    PENDING_APPROVAL("승인대기", GroupBuyTone.INFO),
    REJECTED("반려", GroupBuyTone.WARNING),
    SCHEDULED("예약", GroupBuyTone.INFO),
    EXPOSED("노출중", GroupBuyTone.SUCCESS),
    HIDDEN("숨김", GroupBuyTone.WARNING),
    /** 종료 — 종료·조기 마감·중단 공통. 원문은 분쟁 근거로 보관된다. */
    CLOSED("종료", GroupBuyTone.NEUTRAL);

    private final String label;
    private final GroupBuyTone tone;

    public static GroupBuyPostStatus of(GroupBuyPost post, GroupBuy groupBuy) {
        if (post == null) {
            return NOT_WRITTEN;
        }
        if (groupBuy.getStatus().isTerminal()) {
            return CLOSED;
        }
        return switch (post.getReviewStatus()) {
            case DRAFT -> WRITING;
            case PENDING -> PENDING_APPROVAL;
            case REJECTED -> REJECTED;
            case APPROVED -> post.isHidden() ? HIDDEN
                    : groupBuy.getStatus().isSelling() ? EXPOSED
                    : SCHEDULED;
        };
    }
}
