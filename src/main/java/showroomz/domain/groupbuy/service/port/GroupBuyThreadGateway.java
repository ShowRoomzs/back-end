package showroomz.domain.groupbuy.service.port;

import showroomz.domain.groupbuy.entity.GroupBuy;
import showroomz.domain.groupbuy.type.FulfillmentSide;
import showroomz.domain.groupbuy.type.GroupBuyActorType;
import showroomz.domain.groupbuy.type.GroupBuyIssueType;

import java.util.Optional;

/**
 * 연결·소통 포트(설계서 5-3).
 *
 * <p>「스레드 열기」의 PAIR 스레드와 이슈 스레드(C5)·미이행 3자 스레드(C7)를 모두 이 포트로 연다. 3자 스레드는
 * <b>같은 쌍의 두 번째 스레드</b>라 연결·소통의 {@code thread_kind}로 구분한다(30-1 1절). 쌍의 연결이 끊겨
 * 스레드를 붙일 곳이 없으면 개설 메서드는 {@code GROUP_BUY_THREAD_UNAVAILABLE}로 실패한다.
 */
public interface GroupBuyThreadGateway {

    /** 브랜드-인플루언서 PAIR 스레드 — 연결이 끊겼거나 스레드가 아직 없으면 empty. */
    Optional<Long> findPairThreadId(GroupBuy groupBuy);

    /** 이슈 스레드(3자) 개설 — 첫 글은 이슈 내용이다. 개설된 스레드 id를 돌려준다. */
    Long openIssueThread(GroupBuy groupBuy, FulfillmentSide openerSide, GroupBuyIssueType issueType, String content);

    /**
     * 운영자가 여는 이슈 스레드(3자) — 어드민 B5 · B5b(32 설계 8-3). 긴급 중단 건의 사후 이의 창구이기도 하다.
     *
     * @param operatorId 첫 글의 보낸 사람 — 운영자(셀러 ADMIN) id
     */
    Long openAdminIssueThread(GroupBuy groupBuy, Long operatorId, GroupBuyIssueType issueType, String content);

    /** 미이행 3자 스레드 개설 — 첫 글은 미이행 사유다(제20조②③). 공구당 1개라 이미 있으면 그 스레드에 글을 더한다. */
    Long openFulfillmentDisputeThread(GroupBuy groupBuy, FulfillmentSide checkerSide, String reason);

    /** 스레드의 마지막 글을 쓴 쪽 — 「답변 대기」 판정(30-1 2절). 스레드나 글이 없으면 empty. */
    Optional<GroupBuyActorType> findLastSpeaker(Long threadId);
}
