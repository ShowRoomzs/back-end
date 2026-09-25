package showroomz.api.creator.groupbuy.service;

import showroomz.domain.groupbuy.type.GroupBuyEventType;

import java.util.Collections;
import java.util.EnumMap;
import java.util.Map;
import java.util.Set;

/**
 * 스튜디오 이력 화이트리스트 + 이벤트별 detail 정책(31 설계 6-2).
 *
 * <p>{@code group_buy_history.detail}은 파트너 화면 문구에 맞춰 적힌다. 그대로 내리면 <b>브랜드에게 쓴 문장</b>이
 * 인플루언서에게 간다. 그래서 이벤트마다 내릴지 · detail을 내릴지를 이 맵 하나에 둔다.
 *
 * <ul>
 *   <li>맵에 없는 이벤트는 내리지 않는다 — {@code APPEAL_SUBMITTED}(브랜드↔운영자 소명)가 그렇다.
 *       목록은 쿼리의 {@code event_type IN (...)}으로 걸러 걸러내기 전 목록이 메모리에 올라오지 않게 한다.</li>
 *   <li>{@link DetailRule#REASON_LABEL} — detail 원문을 버리고 연결된 요청 행({@code ref_id})의 사유 코드에서 라벨을
 *       다시 만든다. 「 · 이후를 자른다」 같은 문자열 규칙은 detail 문형이 바뀌는 날 메모가 조용히 샌다.</li>
 * </ul>
 */
final class CreatorGroupBuyHistoryPolicy {

    enum DetailRule {
        /** detail 그대로 */
        KEEP,
        /** 이벤트는 내리고 detail은 버린다 */
        DROP,
        /** detail을 버리고 요청 행의 사유 라벨로 다시 만든다 — 메모가 나가지 않는다 */
        REASON_LABEL
    }

    private static final Map<GroupBuyEventType, DetailRule> RULES;

    static {
        Map<GroupBuyEventType, DetailRule> rules = new EnumMap<>(GroupBuyEventType.class);
        rules.put(GroupBuyEventType.CREATED, DetailRule.KEEP);
        // 최소 준비 물량 스냅샷(「크림 300개」)은 브랜드 소관이라 버린다(B1).
        rules.put(GroupBuyEventType.STOCK_CONFIRMED, DetailRule.DROP);
        rules.put(GroupBuyEventType.POST_SUBMITTED, DetailRule.KEEP);
        rules.put(GroupBuyEventType.OPEN_APPROVED, DetailRule.KEEP);
        rules.put(GroupBuyEventType.OPEN_REJECTED, DetailRule.KEEP);
        rules.put(GroupBuyEventType.READY, DetailRule.KEEP);
        rules.put(GroupBuyEventType.OPENED, DetailRule.KEEP);
        rules.put(GroupBuyEventType.ENDED, DetailRule.KEEP);
        rules.put(GroupBuyEventType.POST_HIDDEN, DetailRule.KEEP);
        rules.put(GroupBuyEventType.POST_UNHIDDEN, DetailRule.KEEP);
        rules.put(GroupBuyEventType.EXTENSION_REQUESTED, DetailRule.KEEP);
        rules.put(GroupBuyEventType.EXTENSION_ACCEPTED, DetailRule.KEEP);
        rules.put(GroupBuyEventType.EXTENSION_REJECTED, DetailRule.KEEP);
        rules.put(GroupBuyEventType.EXTENSION_EXPIRED, DetailRule.KEEP);
        // 요청 메모는 운영자에게 쓴 글이다(31 설계 4-6) — 사유 라벨만.
        rules.put(GroupBuyEventType.SUSPENSION_REQUESTED, DetailRule.REASON_LABEL);
        rules.put(GroupBuyEventType.EARLY_CLOSE_REQUESTED, DetailRule.REASON_LABEL);
        rules.put(GroupBuyEventType.SUSPENSION_REJECTED, DetailRule.KEEP);
        rules.put(GroupBuyEventType.EARLY_CLOSE_REJECTED, DetailRule.KEEP);
        rules.put(GroupBuyEventType.SUSPENDED, DetailRule.KEEP);
        rules.put(GroupBuyEventType.EARLY_CLOSED, DetailRule.KEEP);
        rules.put(GroupBuyEventType.SUSPENSION_NOTICED, DetailRule.KEEP);
        rules.put(GroupBuyEventType.SUSPENSION_WITHDRAWN, DetailRule.KEEP);
        rules.put(GroupBuyEventType.SUSPENDED_BY_ADMIN, DetailRule.KEEP);
        rules.put(GroupBuyEventType.SUSPENDED_EMERGENCY, DetailRule.KEEP);
        // APPEAL_SUBMITTED — 넣지 않는다. 브랜드의 소명은 브랜드↔운영자 사이의 제출물이다.
        rules.put(GroupBuyEventType.ISSUE_OPENED, DetailRule.KEEP);
        rules.put(GroupBuyEventType.FULFILLMENT_CONFIRMED, DetailRule.KEEP);
        rules.put(GroupBuyEventType.FULFILLMENT_DISPUTED, DetailRule.KEEP);
        rules.put(GroupBuyEventType.FULFILLMENT_AUTO_CONFIRMED, DetailRule.KEEP);
        rules.put(GroupBuyEventType.FULFILLMENT_AGREED, DetailRule.KEEP);
        rules.put(GroupBuyEventType.FULFILLMENT_RESOLVED, DetailRule.KEEP);
        rules.put(GroupBuyEventType.SALES_FINALIZED, DetailRule.KEEP);
        rules.put(GroupBuyEventType.SETTLED, DetailRule.KEEP);
        RULES = Collections.unmodifiableMap(rules);
    }

    private CreatorGroupBuyHistoryPolicy() {
    }

    static Set<GroupBuyEventType> visibleEvents() {
        return RULES.keySet();
    }

    static DetailRule ruleOf(GroupBuyEventType eventType) {
        return RULES.get(eventType);
    }
}
