package showroomz.domain.groupbuy.service.port;

import showroomz.domain.groupbuy.entity.GroupBuy;
import showroomz.domain.groupbuy.type.FulfillmentSide;
import showroomz.domain.groupbuy.type.GroupBuyIssueType;

import java.util.Optional;

/**
 * 연결·소통 포트(설계서 5-3).
 *
 * <p>「스레드 열기」의 PAIR 스레드는 지금 모델로 해석된다. 이슈 스레드(C5)·미이행 3자 스레드(C7)는
 * <b>같은 쌍의 두 번째 스레드</b>가 필요한데 {@code message_thread.connection_id}가 UNIQUE라 만들 수 없다 —
 * 연결·소통 설계에 {@code thread_kind} 추가가 선행돼야 한다. 그 전까지 두 메서드는 실패한다.
 */
public interface GroupBuyThreadGateway {

    /** 브랜드-인플루언서 PAIR 스레드 — 연결이 끊겼거나 스레드가 아직 없으면 empty. */
    Optional<Long> findPairThreadId(GroupBuy groupBuy);

    /** 이슈 스레드(3자) 개설 — 첫 글은 이슈 내용이다. 개설된 스레드 id를 돌려준다. */
    Long openIssueThread(GroupBuy groupBuy, FulfillmentSide openerSide, GroupBuyIssueType issueType, String content);

    /** 운영자가 여는 이슈 스레드(3자) — 어드민 B5 · B5b(32 설계 8-3). 긴급 중단 건의 사후 이의 창구이기도 하다. */
    Long openAdminIssueThread(GroupBuy groupBuy, GroupBuyIssueType issueType, String content);

    /** 미이행 3자 스레드 개설 — 첫 글은 미이행 사유다(제20조②③). */
    Long openFulfillmentDisputeThread(GroupBuy groupBuy, FulfillmentSide checkerSide, String reason);
}
