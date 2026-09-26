package showroomz.domain.groupbuy.service;

import showroomz.domain.groupbuy.entity.GroupBuy;
import showroomz.domain.groupbuy.type.GroupBuyActorType;

/**
 * 이력의 행위자 — {@code group_buy_history.actor_*} 세 칸의 묶음.
 *
 * <p>운영자 실명을 스냅샷한다(32 설계 1-4 ①). 파트너·스튜디오 직렬화는 ADMIN 행의 이름을 null로 내린다 —
 * 호칭 「운영자」는 FE 상수다.
 *
 * @param id 브랜드는 마켓 id · 인플루언서는 크리에이터 id · 운영자는 셀러(ADMIN) id — 계약 이력과 같은 규칙
 */
public record GroupBuyActor(GroupBuyActorType type, Long id, String displayName) {

    public static final GroupBuyActor SYSTEM = new GroupBuyActor(GroupBuyActorType.SYSTEM, null, null);

    public static GroupBuyActor admin(Long operatorId, String operatorName) {
        return new GroupBuyActor(GroupBuyActorType.ADMIN, operatorId, operatorName);
    }

    public static GroupBuyActor seller(GroupBuy groupBuy) {
        return new GroupBuyActor(GroupBuyActorType.SELLER,
                groupBuy.getMarket().getId(), groupBuy.getMarket().getMarketName());
    }

    public static GroupBuyActor creator(GroupBuy groupBuy) {
        return new GroupBuyActor(GroupBuyActorType.CREATOR,
                groupBuy.getCreator().getId(), groupBuy.getCreator().getShowroomName());
    }
}
