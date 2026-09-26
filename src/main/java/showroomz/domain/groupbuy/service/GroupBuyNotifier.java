package showroomz.domain.groupbuy.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import showroomz.domain.groupbuy.entity.GroupBuy;

/**
 * 공구 알림 — <b>훅 자리만 두고 발송은 스텁</b>이다(설계서 5-4). 계약의 {@code ContractNotifier}와 같다.
 *
 * <p>채널(메일 / 웹 알림센터)이 정해지지 않았다(26 미결 #6). 호출 지점은 지금 박아 둔다 —
 * 채널이 정해지면 이 클래스 안쪽만 채우면 된다.
 */
@Slf4j
@Component
public class GroupBuyNotifier {

    public void notifyBothParties(GroupBuy groupBuy, String event) {
        notifySeller(groupBuy, event);
        notifyCreator(groupBuy, event);
    }

    public void notifySeller(GroupBuy groupBuy, String event) {
        log.info("[group-buy-notify:stub] to=SELLER event={} groupBuyId={} marketId={}",
                event, groupBuy.getId(), groupBuy.getMarket().getId());
    }

    public void notifyCreator(GroupBuy groupBuy, String event) {
        log.info("[group-buy-notify:stub] to=CREATOR event={} groupBuyId={} creatorId={}",
                event, groupBuy.getId(), groupBuy.getCreator().getId());
    }

    public void notifyAdmin(GroupBuy groupBuy, String event) {
        log.info("[group-buy-notify:stub] to=ADMIN event={} groupBuyId={} number={}",
                event, groupBuy.getId(), groupBuy.getGroupBuyNumber());
    }
}
