package showroomz.domain.groupbuy.service;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import showroomz.domain.groupbuy.entity.GroupBuy;
import showroomz.domain.groupbuy.entity.GroupBuyPost;
import showroomz.domain.groupbuy.repository.GroupBuyPostRepository;
import showroomz.domain.groupbuy.repository.GroupBuyRepository;
import showroomz.domain.groupbuy.type.GroupBuyActorType;
import showroomz.domain.groupbuy.type.GroupBuyEventType;
import showroomz.domain.groupbuy.type.GroupBuyStatus;

import java.time.LocalDateTime;
import java.util.EnumSet;

/**
 * 준비 게이트 판정 — PREPARING → READY(설계서 3-2).
 *
 * <pre>
 * ① 브랜드 최소 물량 확보 확인 · ② 인플루언서 게시물 제출 · ③ 운영자 오픈 승인
 * </pre>
 *
 * <p>게이트를 바꾸는 트랜잭션마다(①·③) 부른다. <b>어느 쪽이 마지막이든 같은 메서드</b>이고, 전이 이력의
 * 행위자는 마지막으로 채운 쪽이다. 호출자는 판정 전에 공구 행을 {@code PESSIMISTIC_WRITE}로 잠가야 한다 —
 * 두 게이트가 동시에 채워지면 둘 다 「상대가 아직」으로 읽고 아무도 전이하지 않을 수 있다.
 */
@Component
@RequiredArgsConstructor
public class GroupBuyReadiness {

    private final GroupBuyRepository groupBuyRepository;
    private final GroupBuyPostRepository postRepository;
    private final GroupBuyHistoryRecorder historyRecorder;
    private final ProductGroupBuyStatusSynchronizer productSynchronizer;
    private final GroupBuyNotifier notifier;

    public static boolean gatesSatisfied(GroupBuy groupBuy, GroupBuyPost post) {
        return groupBuy.isStockConfirmed()
                && post != null && post.isSubmitted()
                && post.isApproved();
    }

    /**
     * 게이트가 모두 찼으면 READY로 올린다. 올렸으면 true.
     *
     * @param recordReadyEvent 운영자 오픈 승인이 마지막이면 {@code OPEN_APPROVED} 이력이 준비완료를 함께 말하므로
     *                         별도 READY 이력을 남기지 않는다(시안 B3 「운영자 오픈 승인 · 준비완료」).
     */
    @Transactional(propagation = Propagation.MANDATORY)
    public boolean promoteIfSatisfied(GroupBuy lockedGroupBuy, GroupBuyActorType actorType, Long actorId,
                                      String actorDisplayName, boolean recordReadyEvent, LocalDateTime now) {
        if (lockedGroupBuy.getStatus() != GroupBuyStatus.PREPARING) {
            return false;
        }
        GroupBuyPost post = postRepository.findByGroupBuyId(lockedGroupBuy.getId()).orElse(null);
        if (!gatesSatisfied(lockedGroupBuy, post)) {
            return false;
        }
        if (groupBuyRepository.transition(lockedGroupBuy.getId(),
                EnumSet.of(GroupBuyStatus.PREPARING), GroupBuyStatus.READY) != 1) {
            return false;
        }
        lockedGroupBuy.applyReady(now);
        if (recordReadyEvent) {
            historyRecorder.record(lockedGroupBuy, GroupBuyEventType.READY, actorType, actorId,
                    actorDisplayName, null, now);
        }
        productSynchronizer.resync(lockedGroupBuy);
        notifier.notifyBothParties(lockedGroupBuy, "READY");
        return true;
    }
}
