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
    /**
     * 노출 중지 — 운영자 조치(1021 설계서 2-1 · 기-08 「숨김」→「노출 중지」 · 위험색). 일반 게시물의 {@code PostStatus.SUSPENDED}
     * (「노출 중지」 · 이의 기한 뒤 삭제)와 다른 조치라 값 이름을 {@code EXPOSURE_} 로 구분한다. 이력 · 알림 · 열 이름(hidden_*)은
     * DB 저장 문자열이라 그대로 둔다.
     */
    EXPOSURE_SUSPENDED("노출 중지", GroupBuyTone.DANGER),
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
            case APPROVED -> post.isHidden() ? EXPOSURE_SUSPENDED
                    : groupBuy.getStatus().isSelling() ? EXPOSED
                    : SCHEDULED;
        };
    }
}
