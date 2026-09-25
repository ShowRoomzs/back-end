package showroomz.domain.groupbuy.service;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import showroomz.domain.groupbuy.entity.GroupBuy;
import showroomz.domain.groupbuy.entity.GroupBuyHistory;
import showroomz.domain.groupbuy.repository.GroupBuyHistoryRepository;
import showroomz.domain.groupbuy.type.GroupBuyActorType;
import showroomz.domain.groupbuy.type.GroupBuyEventType;

import java.time.LocalDateTime;

/**
 * 공구 이력 기록(설계서 3-5). 이벤트 리스너로 분리하지 않고 서비스가 직접 호출한다 —
 * 최소 물량 확인 기록은 제25조 제재 판정의 증거라 이력이 비는 순간 증거가 사라진다.
 *
 * <p>{@code MANDATORY}로 잠근다. 호출자가 트랜잭션 없이 부르면 즉시 실패하므로 전이와 이력이 따로 커밋되지 않는다.
 */
@Component
@RequiredArgsConstructor
public class GroupBuyHistoryRecorder {

    /** {@code group_buy_history.detail} 컬럼 폭 — 넘치면 잘라서라도 남긴다. 이력이 비는 것보다 낫다. */
    private static final int DETAIL_MAX = 500;

    private final GroupBuyHistoryRepository historyRepository;

    @Transactional(propagation = Propagation.MANDATORY)
    public void record(GroupBuy groupBuy, GroupBuyEventType eventType, GroupBuyActorType actorType,
                       Long actorId, String actorDisplayName, String detail, LocalDateTime occurredAt) {
        record(groupBuy, eventType, actorType, actorId, actorDisplayName, detail, null, occurredAt);
    }

    /** @param refId 이력을 만든 사실 행(요청·통지·확인)의 id — 읽는 서피스가 원천에서 문구를 다시 만든다(31 설계 6-2) */
    @Transactional(propagation = Propagation.MANDATORY)
    public void record(GroupBuy groupBuy, GroupBuyEventType eventType, GroupBuyActorType actorType,
                       Long actorId, String actorDisplayName, String detail, Long refId, LocalDateTime occurredAt) {
        historyRepository.save(GroupBuyHistory.builder()
                .groupBuy(groupBuy)
                .eventType(eventType)
                .actorType(actorType)
                .actorId(actorId)
                .actorDisplayName(actorDisplayName)
                .detail(truncate(detail))
                .refId(refId)
                .occurredAt(occurredAt)
                .build());
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public void record(GroupBuy groupBuy, GroupBuyEventType eventType, GroupBuyActor actor, String detail, Long refId,
                       LocalDateTime occurredAt) {
        record(groupBuy, eventType, actor.type(), actor.id(), actor.displayName(), detail, refId, occurredAt);
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public void recordBySystem(GroupBuy groupBuy, GroupBuyEventType eventType, String detail,
                               LocalDateTime occurredAt) {
        record(groupBuy, eventType, GroupBuyActorType.SYSTEM, null, null, detail, occurredAt);
    }

    /** 브랜드가 주체인 이력 — 표시명은 브랜드명 스냅샷이다. actorId는 마켓 id다(계약 이력과 같은 규칙). */
    @Transactional(propagation = Propagation.MANDATORY)
    public void recordBySeller(GroupBuy groupBuy, GroupBuyEventType eventType, String detail,
                               LocalDateTime occurredAt) {
        recordBySeller(groupBuy, eventType, detail, null, occurredAt);
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public void recordBySeller(GroupBuy groupBuy, GroupBuyEventType eventType, String detail, Long refId,
                               LocalDateTime occurredAt) {
        record(groupBuy, eventType, GroupBuyActorType.SELLER,
                groupBuy.getMarket().getId(), groupBuy.getMarket().getMarketName(), detail, refId, occurredAt);
    }

    /** 인플루언서가 주체인 이력 — 표시명은 쇼룸명 스냅샷이다. actorId는 크리에이터 id다. */
    @Transactional(propagation = Propagation.MANDATORY)
    public void recordByCreator(GroupBuy groupBuy, GroupBuyEventType eventType, String detail, Long refId,
                                LocalDateTime occurredAt) {
        record(groupBuy, eventType, GroupBuyActorType.CREATOR,
                groupBuy.getCreator().getId(), groupBuy.getCreator().getShowroomName(), detail, refId, occurredAt);
    }

    private static String truncate(String detail) {
        if (detail == null || detail.length() <= DETAIL_MAX) {
            return detail;
        }
        return detail.substring(0, DETAIL_MAX - 1) + "…";
    }
}
