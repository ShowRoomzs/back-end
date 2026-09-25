package showroomz.api.creator.groupbuy.service;

import org.springframework.stereotype.Component;
import showroomz.api.creator.groupbuy.dto.CreatorGroupBuyDetailResponse;
import showroomz.domain.groupbuy.entity.GroupBuy;
import showroomz.domain.groupbuy.entity.GroupBuyExtensionRequest;
import showroomz.domain.groupbuy.entity.GroupBuyPost;
import showroomz.domain.groupbuy.service.GroupBuyFacts;
import showroomz.domain.groupbuy.type.FulfillmentSide;
import showroomz.domain.groupbuy.type.GroupBuyStatus;
import showroomz.domain.post.policy.GroupBuyPostPolicy;

import java.time.LocalDateTime;

/**
 * 스튜디오 버튼 판정 6종(31 설계 4-8). <b>실행 API가 같은 메서드를 다시 호출해</b> 409로 막는다 —
 * 버튼 판정과 집행 판정이 한 메서드라 어긋날 수 없다.
 */
@Component
public class CreatorGroupBuyPermissionPolicy {

    public CreatorGroupBuyDetailResponse.Permissions evaluate(GroupBuyFacts facts, boolean hasPairThread,
                                                              LocalDateTime now) {
        return new CreatorGroupBuyDetailResponse.Permissions(
                canWritePost(facts),
                canEditPost(facts),
                canRespondExtension(facts, now),
                canRequestSuspension(facts),
                canCheckFulfillment(facts),
                hasPairThread);
    }

    /** 임시저장과 제출이 같은 조건 — PREPARING ∧ 게시물 없음·작성중·반려. 승인대기는 취소·재제출도 불가(B2). */
    public boolean canWritePost(GroupBuyFacts facts) {
        GroupBuyPost post = facts.post();
        return facts.groupBuy().getStatus() == GroupBuyStatus.PREPARING && (post == null || post.isWritable());
    }

    /** 승인 ∧ 공구 READY · IN_PROGRESS(숨김 포함) — 중단 예정·종결은 잠근다(31 설계 2-5). */
    public boolean canEditPost(GroupBuyFacts facts) {
        return GroupBuyPostPolicy.isEditable(facts.post(), facts.groupBuy());
    }

    /**
     * 연장 PENDING ∧ IN_PROGRESS ∧ now &lt; end_at — 응답 기한은 현재 종료 시각이다(§29-6).
     * 중단 예정에서는 응답할 수 없다 — B13에 액션이 없고, 철회되면 다시 열린다.
     */
    public boolean canRespondExtension(GroupBuyFacts facts, LocalDateTime now) {
        GroupBuy groupBuy = facts.groupBuy();
        GroupBuyExtensionRequest extension = facts.extension();
        return extension != null && extension.isPending()
                && groupBuy.getStatus() == GroupBuyStatus.IN_PROGRESS
                && now.isBefore(groupBuy.getEndAt());
    }

    /**
     * IN_PROGRESS ∧ 검토 중 요청 없음(요청자 무관) ∧ 숨김 아님.
     *
     * <ul>
     *   <li>준비완료에서는 불가 — 시안 머리말 「요청할 수 있는 시점은 진행중뿐」. 브랜드와 비대칭이다(31 설계 10-1 #11).</li>
     *   <li>숨김 중 불가 — 파트너와 같다. 숨김 중 상품 하자는 스레드 → 운영자 직권 중단 경로가 남는다.</li>
     *   <li>연장 대기 중에는 가능 — B6 「연장 응답과 별개로 공구 중단은 언제든 요청할 수 있습니다」.</li>
     * </ul>
     */
    public boolean canRequestSuspension(GroupBuyFacts facts) {
        return facts.groupBuy().getStatus() == GroupBuyStatus.IN_PROGRESS
                && facts.pendingChangeRequest().isEmpty()
                && !facts.isPostHidden();
    }

    /** ENDED ∧ 내(CREATOR) 확인 없음. 기한이 지나도 받는다 — 자동 이행 스위치가 꺼진 동안 기한은 표시값이다. */
    public boolean canCheckFulfillment(GroupBuyFacts facts) {
        return facts.groupBuy().getStatus() == GroupBuyStatus.ENDED
                && facts.fulfillmentCheck(FulfillmentSide.CREATOR).isEmpty();
    }
}
