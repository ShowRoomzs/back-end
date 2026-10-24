package showroomz.domain.metrics.repository;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.springframework.stereotype.Repository;
import showroomz.domain.contract.entity.ContractItem;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 어드민 운영 지표(1017 설계서 7-2) 원천 — <b>집계 전용 · 저장 없음</b>. MVP 규모(공구 수십 · 주문 수백)라 기간의 원천 행을 읽어
 * 서비스가 중앙값 · 비율을 계산한다. 쿼리는 전부 기간 [from, to) 로 자른다.
 */
@Repository
public class MetricsQueryRepository {

    @PersistenceContext
    private EntityManager em;

    // ------------------------------------------------------------------ 거래 · 수요

    /** 공구 결제 줄 — 결제 시각 기준 · 취소 포함. */
    public List<PaidLine> paidLines(LocalDateTime from, LocalDateTime to) {
        List<Object[]> rows = em.createQuery("SELECT o.id, o.user.id, o.paidAt, g.id, g.creator.id, g.market.id, "
                        + "op.price * op.quantity, op.quantity, op.status FROM OrderProduct op JOIN op.order o JOIN op.groupBuy g "
                        + "WHERE o.paidAt >= :from AND o.paidAt < :to", Object[].class)
                .setParameter("from", from).setParameter("to", to).getResultList();
        List<PaidLine> lines = new ArrayList<>(rows.size());
        for (Object[] r : rows) {
            lines.add(new PaidLine((Long) r[0], (Long) r[1], (LocalDateTime) r[2], (Long) r[3], (Long) r[4], (Long) r[5],
                    ((Number) r[6]).longValue(), ((Number) r[7]).longValue(), "CANCELLED".equals(String.valueOf(r[8]))));
        }
        return lines;
    }

    /** 배송완료 하위주문 — 공구별 [공구 id, 건수]. 반품 · 교환율의 분모(배송완료 기준). */
    public Map<Long, Long> deliveredByGroupBuy(LocalDateTime from, LocalDateTime to) {
        return toLongMap(em.createQuery("SELECT g.groupBuy.id, COUNT(g) FROM OrderDeliveryGroup g "
                        + "WHERE g.deliveredAt >= :from AND g.deliveredAt < :to GROUP BY g.groupBuy.id", Object[].class)
                .setParameter("from", from).setParameter("to", to).getResultList());
    }

    /** 반품 · 교환 접수(결제 대기 제외) — 브랜드 귀책 = 배송비 브랜드 부담 ∨ 귀책 변경. */
    public List<ClaimLine> claims(LocalDateTime from, LocalDateTime to) {
        List<Object[]> rows = em.createQuery("SELECT c.type, c.feeBearer, c.faultChangedToSeller, op.groupBuy.id "
                        + "FROM OrderClaim c JOIN c.orderProduct op WHERE c.requestedAt >= :from AND c.requestedAt < :to "
                        + "AND c.status <> showroomz.domain.order.type.ClaimStatus.PAYMENT_PENDING", Object[].class)
                .setParameter("from", from).setParameter("to", to).getResultList();
        List<ClaimLine> claims = new ArrayList<>(rows.size());
        for (Object[] r : rows) {
            boolean sellerFault = "SELLER".equals(String.valueOf(r[1])) || Boolean.TRUE.equals(r[2]);
            claims.add(new ClaimLine(String.valueOf(r[0]), sellerFault, (Long) r[3]));
        }
        return claims;
    }

    /** 기간 시작 전 결제 이력이 있는 소비자 — 재구매율. */
    public Set<Long> usersPaidBefore(Collection<Long> userIds, LocalDateTime before) {
        if (userIds.isEmpty()) {
            return Set.of();
        }
        return new HashSet<>(em.createQuery("SELECT DISTINCT o.user.id FROM Order o WHERE o.user.id IN :ids "
                        + "AND o.paidAt < :before", Long.class)
                .setParameter("ids", userIds).setParameter("before", before).getResultList());
    }

    /** 팔로우 이벤트 — [인플루언서 id, 소비자 id] → 가장 이른 팔로우 시각. 언팔로우로 지워지는 팔로우 표 대신 이벤트 표를 쓴다. */
    public Map<String, LocalDateTime> firstFollows(Collection<Long> creatorIds, Collection<Long> userIds) {
        Map<String, LocalDateTime> follows = new HashMap<>();
        if (creatorIds.isEmpty() || userIds.isEmpty()) {
            return follows;
        }
        for (Object[] r : em.createQuery("SELECT e.creatorId, e.userId, MIN(e.occurredAt) FROM CreatorFollowEvent e "
                        + "WHERE e.eventType = showroomz.domain.member.creator.type.FollowEventType.FOLLOW "
                        + "AND e.creatorId IN :creators AND e.userId IN :users GROUP BY e.creatorId, e.userId", Object[].class)
                .setParameter("creators", creatorIds).setParameter("users", userIds).getResultList()) {
            follows.put(r[0] + ":" + r[1], (LocalDateTime) r[2]);
        }
        return follows;
    }

    /** 기간 끝 시점 팔로워 수 — 인플루언서별. */
    public Map<Long, Long> followersAt(Collection<Long> creatorIds, LocalDateTime at) {
        if (creatorIds.isEmpty()) {
            return Map.of();
        }
        return toLongMap(em.createQuery("SELECT cf.creator.id, COUNT(cf) FROM CreatorFollow cf WHERE cf.creator.id IN :ids "
                        + "AND cf.createdAt < :at GROUP BY cf.creator.id", Object[].class)
                .setParameter("ids", creatorIds).setParameter("at", at).getResultList());
    }

    /** 신규 소비자 — 가입 경로별. */
    public Map<String, Long> signupsByProvider(LocalDateTime from, LocalDateTime to) {
        Map<String, Long> counts = new HashMap<>();
        for (Object[] r : em.createQuery("SELECT u.providerType, COUNT(u) FROM Users u "
                        + "WHERE u.roleType = showroomz.api.app.auth.entity.RoleType.USER "
                        + "AND u.createdAt >= :from AND u.createdAt < :to GROUP BY u.providerType", Object[].class)
                .setParameter("from", from).setParameter("to", to).getResultList()) {
            counts.merge(String.valueOf(r[0]), ((Number) r[1]).longValue(), Long::sum);
        }
        return counts;
    }

    /** 소비자 탈퇴 — 상태 이력(탈퇴 시각 열이 없다). */
    public long countUserWithdrawals(LocalDateTime from, LocalDateTime to) {
        return count("SELECT COUNT(h) FROM UserStatusHistory h "
                + "WHERE h.newStatus = showroomz.domain.member.user.type.UserStatus.WITHDRAWN "
                + "AND h.createdAt >= :from AND h.createdAt < :to", from, to);
    }

    // ------------------------------------------------------------------ 공급

    /** 이 시각 전에 오픈한 공구가 있는 인플루언서 · 브랜드 — 신규(첫 공구) / 재공구 구분. */
    public Set<Long> creatorsOpenedBefore(Collection<Long> creatorIds, LocalDateTime before) {
        if (creatorIds.isEmpty()) {
            return Set.of();
        }
        return new HashSet<>(em.createQuery("SELECT DISTINCT g.creator.id FROM GroupBuy g WHERE g.creator.id IN :ids "
                        + "AND g.openedAt < :before", Long.class)
                .setParameter("ids", creatorIds).setParameter("before", before).getResultList());
    }

    public Set<Long> marketsOpenedBefore(Collection<Long> marketIds, LocalDateTime before) {
        if (marketIds.isEmpty()) {
            return Set.of();
        }
        return new HashSet<>(em.createQuery("SELECT DISTINCT g.market.id FROM GroupBuy g WHERE g.market.id IN :ids "
                        + "AND g.openedAt < :before", Long.class)
                .setParameter("ids", marketIds).setParameter("before", before).getResultList());
    }

    /** 오픈 이력이 있는 공구 전부 — [공구 id, 인플루언서 id, 시작, 종료(실제), 상태, 계약 체결, 오픈]. 공구 수가 적어 한 번에 읽는다. */
    public List<GroupBuyLine> openedGroupBuys() {
        List<GroupBuyLine> lines = new ArrayList<>();
        for (Object[] r : em.createQuery("SELECT g.id, g.creator.id, g.market.id, g.startAt, COALESCE(g.endedAt, g.endAt), "
                + "g.status, c.concludedAt, g.openedAt, c.title, m.marketName, cr.showroomName, c.fixedFeeAmount "
                + "FROM GroupBuy g JOIN g.contract c JOIN g.market m JOIN g.creator cr WHERE g.openedAt IS NOT NULL", Object[].class)
                .getResultList()) {
            lines.add(new GroupBuyLine((Long) r[0], (Long) r[1], (Long) r[2], (LocalDateTime) r[3], (LocalDateTime) r[4],
                    String.valueOf(r[5]), (LocalDateTime) r[6], (LocalDateTime) r[7], (String) r[8], (String) r[9],
                    (String) r[10], r[11] == null ? 0L : ((Number) r[11]).longValue()));
        }
        return lines;
    }

    /** 체결 계약 — [브랜드 id, 체결 시각]. 재계약률 · 주별 체결 차트. */
    public List<Object[]> concludedContracts(LocalDateTime before) {
        return em.createQuery("SELECT c.market.id, c.concludedAt FROM Contract c WHERE c.concludedAt IS NOT NULL "
                        + "AND c.concludedAt < :before AND c.deletedAt IS NULL", Object[].class)
                .setParameter("before", before).getResultList();
    }

    /** 연결 → 체결 — 기간 중 요청된 연결(PAIR) 수 · 그중 그 연결로 체결된 계약이 있는 수. */
    public long[] connectionConversion(LocalDateTime from, LocalDateTime to) {
        long requested = count("SELECT COUNT(c) FROM Connection c WHERE c.type = showroomz.domain.connection.type.ConnectionType.PAIR "
                + "AND c.requestedAt >= :from AND c.requestedAt < :to", from, to);
        long concluded = count("SELECT COUNT(DISTINCT k.connection.id) FROM Contract k JOIN k.connection c "
                + "WHERE c.type = showroomz.domain.connection.type.ConnectionType.PAIR "
                + "AND c.requestedAt >= :from AND c.requestedAt < :to "
                + "AND k.status = showroomz.domain.contract.type.ContractStatus.CONCLUDED", from, to);
        return new long[]{requested, concluded};
    }

    /** 신규 입점 — [브랜드 승인, 인플루언서 승인]. */
    public long[] approvedOnboarding(LocalDateTime from, LocalDateTime to) {
        long brands = count("SELECT COUNT(a) FROM SellerApplication a JOIN a.seller s "
                + "WHERE a.status = showroomz.api.seller.auth.type.SellerStatus.APPROVED "
                + "AND s.roleType = showroomz.api.app.auth.entity.RoleType.SELLER "
                + "AND a.processedAt >= :from AND a.processedAt < :to", from, to);
        long creators = count("SELECT COUNT(a) FROM CreatorApplication a "
                + "WHERE a.status = showroomz.domain.member.creator.type.CreatorApplicationStatus.APPROVED "
                + "AND a.processedAt >= :from AND a.processedAt < :to", from, to);
        return new long[]{brands, creators};
    }

    // ------------------------------------------------------------------ 공구

    /** 공구별 판매 수량(취소 제외) — 최소 물량 달성 판정. 결제 시각 무관 · 공구 전체. */
    public Map<Long, Long> soldQuantityByGroupBuy(Collection<Long> groupBuyIds) {
        if (groupBuyIds.isEmpty()) {
            return Map.of();
        }
        return toLongMap(em.createQuery("SELECT op.groupBuy.id, COALESCE(SUM(op.quantity), 0) FROM OrderProduct op JOIN op.order o "
                        + "WHERE op.groupBuy.id IN :ids AND o.paidAt IS NOT NULL "
                        + "AND op.status <> showroomz.domain.order.type.OrderProductStatus.CANCELLED "
                        + "GROUP BY op.groupBuy.id", Object[].class)
                .setParameter("ids", groupBuyIds).getResultList());
    }

    /** 공구별 거래액(결제 · 취소 포함) — 공구 전체. ROI 분자. */
    public Map<Long, Long> grossByGroupBuy(Collection<Long> groupBuyIds) {
        if (groupBuyIds.isEmpty()) {
            return Map.of();
        }
        return toLongMap(em.createQuery("SELECT op.groupBuy.id, COALESCE(SUM(op.price * op.quantity), 0) FROM OrderProduct op "
                        + "JOIN op.order o WHERE op.groupBuy.id IN :ids AND o.paidAt IS NOT NULL GROUP BY op.groupBuy.id",
                        Object[].class)
                .setParameter("ids", groupBuyIds).getResultList());
    }

    /** 공구별 계약 항목 — 최소 물량 합 · 리워드율(정산 전 리워드 추정). */
    public Map<Long, List<ContractItem>> contractItemsByGroupBuy(Collection<Long> groupBuyIds) {
        Map<Long, List<ContractItem>> items = new HashMap<>();
        if (groupBuyIds.isEmpty()) {
            return items;
        }
        for (Object[] r : em.createQuery("SELECT g.id, i FROM GroupBuy g, ContractItem i WHERE i.contract = g.contract "
                        + "AND g.id IN :ids", Object[].class)
                .setParameter("ids", groupBuyIds).getResultList()) {
            items.computeIfAbsent((Long) r[0], k -> new ArrayList<>()).add((ContractItem) r[1]);
        }
        return items;
    }

    /** 정산된 공구의 리워드 — [공구 id, 리워드]. */
    public Map<Long, Long> rewardByGroupBuy(Collection<Long> groupBuyIds) {
        if (groupBuyIds.isEmpty()) {
            return Map.of();
        }
        return toLongMap(em.createQuery("SELECT s.groupBuy.id, s.rewardAmount FROM Settlement s WHERE s.groupBuy.id IN :ids",
                        Object[].class)
                .setParameter("ids", groupBuyIds).getResultList());
    }

    /** 중단 — [승인된 중단 요청(신청), 집행된 직권 중단(직권)]. 지표 값은 둘의 합이다. */
    public long[] countSuspensionsByKind(LocalDateTime from, LocalDateTime to) {
        long requested = count("SELECT COUNT(r) FROM GroupBuyChangeRequest r "
                + "WHERE r.requestType = showroomz.domain.groupbuy.type.ChangeRequestType.SUSPEND "
                + "AND r.status = showroomz.domain.groupbuy.type.ChangeRequestStatus.APPROVED "
                + "AND r.decidedAt >= :from AND r.decidedAt < :to", from, to);
        long byAdmin = count("SELECT COUNT(s) FROM GroupBuyAdminSuspension s WHERE s.executedAt >= :from AND s.executedAt < :to",
                from, to);
        return new long[]{requested, byAdmin};
    }

    // ------------------------------------------------------------------ 운영

    /** 처리 쌍 — [진입 시각, 처리 시각]. 큐별 평균 소요 · 기한 준수. */
    public List<LocalDateTime[]> processedPairs(String queue, LocalDateTime from, LocalDateTime to) {
        String jpql = switch (queue) {
            case "INQUIRY" -> "SELECT x.createdAt, x.answeredAt FROM OneToOneInquiry x WHERE x.answeredAt >= :from AND x.answeredAt < :to";
            case "SELLER_APPLICATION" -> "SELECT x.createdAt, x.processedAt FROM SellerApplication x "
                    + "WHERE x.processedAt >= :from AND x.processedAt < :to";
            case "CREATOR_APPLICATION" -> "SELECT x.createdAt, x.processedAt FROM CreatorApplication x "
                    + "WHERE x.processedAt >= :from AND x.processedAt < :to";
            case "OPEN_REVIEW" -> "SELECT x.submittedAt, x.reviewedAt FROM GroupBuyPost x "
                    + "WHERE x.reviewedAt >= :from AND x.reviewedAt < :to AND x.submittedAt IS NOT NULL";
            case "POST_REPORT" -> "SELECT x.reportedAt, x.handledAt FROM PostReport x WHERE x.handledAt >= :from AND x.handledAt < :to";
            case "CHANGE_REQUEST" -> "SELECT x.requestedAt, x.processedAt FROM ChangeRequest x "
                    + "WHERE x.processedAt >= :from AND x.processedAt < :to";
            default -> throw new IllegalArgumentException(queue);
        };
        List<LocalDateTime[]> pairs = new ArrayList<>();
        for (Object[] r : em.createQuery(jpql, Object[].class).setParameter("from", from).setParameter("to", to)
                .getResultList()) {
            pairs.add(new LocalDateTime[]{(LocalDateTime) r[0], (LocalDateTime) r[1]});
        }
        return pairs;
    }

    public long countAdjustmentsOpened(LocalDateTime from, LocalDateTime to) {
        return count("SELECT COUNT(a) FROM SettlementAdjustment a WHERE a.openedAt >= :from AND a.openedAt < :to", from, to);
    }

    /** 기간 중 열린 조정 협의 중 지금 합의 대기(협의 중 · {@code OPEN})인 것 — 조회 시점 상태다. */
    public long countAdjustmentsAwaitingAgreement(LocalDateTime from, LocalDateTime to) {
        return count("SELECT COUNT(a) FROM SettlementAdjustment a WHERE a.openedAt >= :from AND a.openedAt < :to "
                + "AND a.status = showroomz.domain.settlement.adjustment.type.AdjustmentStatus.OPEN", from, to);
    }

    public long countSettlementsCreated(LocalDateTime from, LocalDateTime to) {
        return count("SELECT COUNT(s) FROM Settlement s WHERE s.createdAt >= :from AND s.createdAt < :to", from, to);
    }

    public long countPostReports(LocalDateTime from, LocalDateTime to) {
        return count("SELECT COUNT(r) FROM PostReport r WHERE r.reportedAt >= :from AND r.reportedAt < :to", from, to);
    }

    /** 기간 끝까지 공개된 게시물 — 신고율 분모. */
    public long countPublishedPosts(LocalDateTime before) {
        return em.createQuery("SELECT COUNT(p) FROM Post p WHERE p.status = showroomz.domain.post.type.PostStatus.PUBLISHED "
                        + "AND p.publishedAt < :before", Long.class)
                .setParameter("before", before).getSingleResult();
    }

    /**
     * 환불 실패 — 기간 중 실패한 환불 건마다 마지막 실패 사유({@code last_error} · PG 거절 문구 또는 집행 전 검사 문장) 한 줄. 건수 = 크기.
     * 재시도가 성공하면 사유가 지워지므로 그 건은 {@code null} 이다.
     */
    public List<String> refundFailureReasons(LocalDateTime from, LocalDateTime to) {
        return em.createQuery("SELECT t.lastError FROM OrderRefundTask t WHERE t.failedAt >= :from AND t.failedAt < :to",
                        String.class)
                .setParameter("from", from).setParameter("to", to).getResultList();
    }

    // ------------------------------------------------------------------

    private long count(String jpql, LocalDateTime from, LocalDateTime to) {
        Object value = em.createQuery(jpql).setParameter("from", from).setParameter("to", to).getSingleResult();
        return value instanceof Number number ? number.longValue() : 0L;
    }

    private static Map<Long, Long> toLongMap(List<Object[]> rows) {
        Map<Long, Long> map = new HashMap<>();
        for (Object[] r : rows) {
            if (r[0] != null) {
                map.merge((Long) r[0], r[1] == null ? 0L : ((Number) r[1]).longValue(), Long::sum);
            }
        }
        return map;
    }

    public record PaidLine(Long orderId, Long userId, LocalDateTime paidAt, Long groupBuyId, Long creatorId, Long marketId,
                           long amount, long quantity, boolean cancelled) {
    }

    public record ClaimLine(String type, boolean sellerFault, Long groupBuyId) {
    }

    /**
     * @param endAt 실제 종료(조기 마감 · 중단이면 그 시각) — 없으면 예정 종료
     */
    public record GroupBuyLine(Long id, Long creatorId, Long marketId, LocalDateTime startAt, LocalDateTime endAt,
                               String status, LocalDateTime concludedAt, LocalDateTime openedAt, String name,
                               String brandName, String creatorName, long fixedFee) {

        public boolean terminal() {
            return "ENDED".equals(status) || "SETTLED".equals(status) || "SUSPENDED".equals(status);
        }

        /** 기간 [from, to) 중 판매일이 있었는가. */
        public boolean sellingDuring(LocalDateTime from, LocalDateTime to) {
            LocalDateTime sellingFrom = openedAt.isAfter(startAt) ? openedAt : startAt;
            return sellingFrom.isBefore(to) && !endAt.isBefore(from);
        }
    }
}
