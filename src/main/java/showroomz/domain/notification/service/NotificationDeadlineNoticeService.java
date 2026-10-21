package showroomz.domain.notification.service;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import showroomz.domain.notification.type.NotificationImageKind;
import showroomz.domain.notification.type.NotificationTargetType;
import showroomz.domain.notification.type.NotificationType;
import showroomz.domain.notification.type.NotificationActorType;
import showroomz.global.config.properties.OrderProperties;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 하루 전 안내 3종(1017 설계서 1-3 · 1-4 · 1-5) — 매일 10:00 배치. 중복 키로 같은 대상에 한 번만 쓴다(배치가 두 번 돌아도 한 건).
 *
 * <ul>
 *   <li>공구 마감 임박(광고성) — 내일 마감하는 진행 중 공구를 장바구니에 담았거나 그 상품을 좋아요한 소비자</li>
 *   <li>구매확정 D-1 — 내일 자동 구매확정되는 하위주문(타이머가 멈춘 건 제외)</li>
 *   <li>서명 기한 D-1 — 내일이 서명 기한인 서명 진행중 계약의 미서명 당사자(정지 보류 중 제외)</li>
 * </ul>
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class NotificationDeadlineNoticeService {

    @PersistenceContext
    private EntityManager em;

    private final NotificationPublisher publisher;
    private final OrderProperties orderProperties;

    /** @return 발행한 건수(동의 · 중복 판정 전) */
    public int noticeGroupBuyEnding(LocalDateTime now) {
        LocalDateTime from = now.toLocalDate().plusDays(1).atStartOfDay();
        LocalDateTime to = from.plusDays(1);
        List<Object[]> carts = em.createQuery("""
                SELECT DISTINCT c.user.id, gb.id, p.name, p.thumbnailUrl FROM Cart c
                JOIN c.groupBuy gb JOIN c.variant v JOIN v.product p
                WHERE gb.status IN (showroomz.domain.groupbuy.type.GroupBuyStatus.IN_PROGRESS,
                                    showroomz.domain.groupbuy.type.GroupBuyStatus.SUSPENSION_SCHEDULED)
                  AND gb.endAt >= :from AND gb.endAt < :to""", Object[].class)
                .setParameter("from", from).setParameter("to", to).getResultList();
        List<Object[]> wishes = em.createQuery("""
                SELECT DISTINCT w.user.id, gb.id, p.name, p.thumbnailUrl FROM Wishlist w
                JOIN w.product p, ContractItem ci JOIN ci.contract ct, GroupBuy gb
                WHERE ci.product = p AND gb.contract = ct
                  AND gb.status IN (showroomz.domain.groupbuy.type.GroupBuyStatus.IN_PROGRESS,
                                    showroomz.domain.groupbuy.type.GroupBuyStatus.SUSPENSION_SCHEDULED)
                  AND gb.endAt >= :from AND gb.endAt < :to""", Object[].class)
                .setParameter("from", from).setParameter("to", to).getResultList();

        // 사람 · 공구당 한 건 — 장바구니가 좋아요보다 앞선다(문구가 더 구체적이다).
        Map<String, NotificationCommand> commands = new LinkedHashMap<>();
        addEnding(commands, carts, "장바구니에 담은 ");
        addEnding(commands, wishes, "좋아요한 ");
        commands.values().forEach(publisher::publish);
        return commands.size();
    }

    private static void addEnding(Map<String, NotificationCommand> commands, List<Object[]> rows, String prefix) {
        for (Object[] row : rows) {
            Long userId = (Long) row[0];
            Long groupBuyId = (Long) row[1];
            String product = (String) row[2];
            String key = "GB_ENDING:" + userId + ":" + groupBuyId;
            commands.putIfAbsent(key, NotificationCommand.toUser(userId, NotificationType.GROUP_BUY_ENDING_D1)
                    .actorType(NotificationActorType.SYSTEM)
                    .title(prefix + product + " 공구가 내일 마감돼요").emphasis(product)
                    .targetType(NotificationTargetType.GROUP_BUY).targetId(groupBuyId)
                    .imageKind(NotificationImageKind.THUMB).imageRef((String) row[3])
                    .dedupKey(key).build());
        }
    }

    public int noticePurchaseConfirm(LocalDateTime now) {
        int days = orderProperties.getPurchaseConfirmDays();
        LocalDate tomorrow = now.toLocalDate().plusDays(1);
        // 예정 = 기준 시각 + N일 — 예정일이 내일인 하위주문(기준 시각이 내일 − N일).
        LocalDateTime from = tomorrow.minusDays(days).atStartOfDay();
        LocalDateTime to = from.plusDays(1);
        List<Object[]> rows = em.createQuery("""
                SELECT g.id, o.user.id FROM OrderDeliveryGroup g JOIN g.order o
                WHERE g.fulfillmentStatus = showroomz.domain.order.type.FulfillmentStatus.DELIVERED
                  AND g.confirmPausedAt IS NULL
                  AND COALESCE(g.confirmRestartAt, g.deliveredAt) >= :from
                  AND COALESCE(g.confirmRestartAt, g.deliveredAt) < :to""", Object[].class)
                .setParameter("from", from).setParameter("to", to).getResultList();
        List<NotificationCommand> commands = new ArrayList<>();
        for (Object[] row : rows) {
            Long groupId = (Long) row[0];
            OrderLine line = firstLine(groupId);
            commands.add(NotificationCommand.toUser((Long) row[1], NotificationType.ORDER_CONFIRM_D1)
                    .actorType(NotificationActorType.SYSTEM)
                    .title("내일 자동으로 구매확정돼요 · 반품 · 교환은 오늘까지 신청할 수 있어요")
                    .emphasis("내일 자동으로 구매확정")
                    .targetType(NotificationTargetType.ORDER_GROUP).targetId(groupId)
                    .imageKind(NotificationImageKind.THUMB).imageRef(line == null ? null : line.imageUrl())
                    .dedupKey("CONFIRM_D1:" + groupId + ":" + tomorrow).build());
        }
        commands.forEach(publisher::publish);
        return commands.size();
    }

    public int noticeSignatureDeadline(LocalDateTime now) {
        LocalDateTime from = now.toLocalDate().plusDays(1).atStartOfDay();
        LocalDateTime to = from.plusDays(1);
        List<Object[]> rows = em.createQuery("""
                SELECT c.id, c.market.id, c.creator.id, c.title, c.brandSignedAt, c.creatorSignedAt FROM Contract c
                WHERE c.status = showroomz.domain.contract.type.ContractStatus.SIGNING
                  AND c.deletedAt IS NULL AND c.sanctionHoldSince IS NULL
                  AND c.signatureDeadlineAt >= :from AND c.signatureDeadlineAt < :to""", Object[].class)
                .setParameter("from", from).setParameter("to", to).getResultList();
        List<NotificationCommand> commands = new ArrayList<>();
        for (Object[] row : rows) {
            Long contractId = (Long) row[0];
            String title = row[3] == null ? "계약" : (String) row[3];
            String sentence = "「" + title + "」 서명 기한이 내일이에요 · 메일 · 문자로 받은 링크에서 서명해 주세요";
            if (row[4] == null && row[1] != null) {
                commands.add(NotificationCommand.toSeller((Long) row[1], NotificationType.CONTRACT_SIGN_DEADLINE_D1)
                        .actorType(NotificationActorType.SYSTEM).title(sentence)
                        .targetType(NotificationTargetType.CONTRACT).targetId(contractId)
                        .dedupKey("SIGN_D1:SELLER:" + contractId + ":" + from.toLocalDate()).build());
            }
            if (row[5] == null && row[2] != null) {
                commands.add(NotificationCommand.toCreator((Long) row[2], NotificationType.CONTRACT_SIGN_DEADLINE_D1)
                        .actorType(NotificationActorType.SYSTEM).title(sentence)
                        .targetType(NotificationTargetType.CONTRACT).targetId(contractId)
                        .dedupKey("SIGN_D1:CREATOR:" + contractId + ":" + from.toLocalDate()).build());
            }
        }
        commands.forEach(publisher::publish);
        return commands.size();
    }

    /** 하위주문의 첫 항목 — 소비자 알림의 상품명 · 썸네일. */
    public OrderLine firstLine(Long deliveryGroupId) {
        List<Object[]> rows = em.createQuery("""
                SELECT op.productName, op.imageUrl, (SELECT COUNT(x) FROM OrderProduct x WHERE x.deliveryGroup.id = :gid)
                FROM OrderProduct op WHERE op.deliveryGroup.id = :gid ORDER BY op.id ASC""", Object[].class)
                .setParameter("gid", deliveryGroupId).setMaxResults(1).getResultList();
        if (rows.isEmpty()) {
            return null;
        }
        Object[] row = rows.get(0);
        return new OrderLine((String) row[0], (String) row[1], ((Number) row[2]).intValue());
    }

    /** @param count 하위주문의 항목 수 — 2 이상이면 「○○ 외 1건」 */
    public record OrderLine(String productName, String imageUrl, int count) {
        public String label() {
            return count > 1 ? productName + " 외 " + (count - 1) + "건" : productName;
        }
    }
}
