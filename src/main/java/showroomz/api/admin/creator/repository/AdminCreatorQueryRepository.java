package showroomz.api.admin.creator.repository;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.springframework.stereotype.Repository;
import showroomz.domain.connection.entity.Connection;
import showroomz.domain.connection.type.ConnectionStatus;
import showroomz.domain.connection.type.ConnectionType;
import showroomz.domain.contract.entity.Contract;
import showroomz.domain.contract.type.ContractStatus;
import showroomz.domain.groupbuy.type.GroupBuyEventType;
import showroomz.domain.groupbuy.type.GroupBuyPostReviewStatus;
import showroomz.domain.groupbuy.type.GroupBuyStatus;
import showroomz.domain.history.entity.LoginHistory;
import showroomz.domain.member.creator.entity.CreatorApplication;
import showroomz.domain.message.entity.MessageThread;
import showroomz.domain.message.type.ThreadKind;
import showroomz.domain.message.type.ThreadStatus;
import showroomz.domain.post.type.PostStatus;
import showroomz.domain.post.type.PostType;
import showroomz.domain.settlement.adjustment.entity.SettlementAdjustment;
import showroomz.domain.settlement.adjustment.type.AdjustmentStatus;
import showroomz.domain.settlement.entity.Settlement;
import showroomz.domain.settlement.type.PayoutStatus;
import showroomz.domain.settlement.type.SettlementPayee;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * 인플루언서 상세의 읽기 쿼리(53 설계서 3절) — 공구 · 계약 · 정산 · 연결 · 게시물 리포지토리에 화면 전용 메서드를 더하지 않고 여기에 모은다.
 * 전부 인플루언서 1명 범위라 건수가 작다(페이지는 계약만).
 */
@Repository
public class AdminCreatorQueryRepository {

    private static final Set<ConnectionStatus> LISTED_CONNECTION_STATUSES =
            Set.of(ConnectionStatus.CONNECTED, ConnectionStatus.DISCONNECTED);
    private static final Set<PostStatus> LISTED_GENERAL_POST_STATUSES =
            Set.of(PostStatus.PUBLISHED, PostStatus.SUSPENDED, PostStatus.UNDER_REVIEW);
    private static final Set<PayoutStatus> SETTLED_PAYOUTS = PayoutStatus.SETTLED;

    @PersistenceContext
    private EntityManager em;

    // ------------------------------------------------------------------ 기본 정보 · 이력 파생

    /** 그 소비자 계정의 입점 신청 전부(오래된 순) — 가입 신청 · 입점 승인 파생 이력 · 입점 승인일. */
    public List<CreatorApplication> findApplications(Long userId) {
        return em.createQuery("SELECT a FROM CreatorApplication a WHERE a.user.id = :userId ORDER BY a.createdAt ASC, a.id ASC",
                        CreatorApplication.class)
                .setParameter("userId", userId)
                .getResultList();
    }

    public Optional<LoginHistory> findLatestLogin(Long userId) {
        return em.createQuery("SELECT l FROM LoginHistory l WHERE l.user.id = :userId ORDER BY l.loginAt DESC, l.id DESC",
                        LoginHistory.class)
                .setParameter("userId", userId)
                .setMaxResults(1)
                .getResultStream()
                .findFirst();
    }

    /** 운영팀 채널 스레드 id — 읽기 전용(조회에서 채널을 만들지 않는다). */
    public Optional<Long> findOperatorChannelThreadId(Long creatorId) {
        return em.createQuery("SELECT t.id FROM MessageThread t WHERE t.connection.creator.id = :creatorId "
                        + "AND t.connection.type = :type AND t.kind = :kind ORDER BY t.id ASC", Long.class)
                .setParameter("creatorId", creatorId)
                .setParameter("type", ConnectionType.OPERATOR_CREATOR)
                .setParameter("kind", ThreadKind.CONNECTION)
                .setMaxResults(1)
                .getResultStream()
                .findFirst();
    }

    public List<SettlementAdjustment> findOpenAdjustments(Long creatorId) {
        return em.createQuery("SELECT a FROM SettlementAdjustment a WHERE a.creatorId = :creatorId AND a.status = :status "
                        + "ORDER BY a.deadlineAt ASC", SettlementAdjustment.class)
                .setParameter("creatorId", creatorId)
                .setParameter("status", AdjustmentStatus.OPEN)
                .getResultList();
    }

    // ------------------------------------------------------------------ 운영 현황 · 쇼룸 활동

    public long countConnectedBrands(Long creatorId) {
        return em.createQuery("SELECT COUNT(c) FROM Connection c WHERE c.creator.id = :creatorId AND c.type = :type "
                        + "AND c.status = :status", Long.class)
                .setParameter("creatorId", creatorId)
                .setParameter("type", ConnectionType.PAIR)
                .setParameter("status", ConnectionStatus.CONNECTED)
                .getSingleResult();
    }

    public long countSellingGroupBuys(Long creatorId) {
        return em.createQuery("SELECT COUNT(g) FROM GroupBuy g WHERE g.creator.id = :creatorId AND g.status IN :statuses",
                        Long.class)
                .setParameter("creatorId", creatorId)
                .setParameter("statuses", GroupBuyStatus.SELLING)
                .getSingleResult();
    }

    /** 정산 진행 중 리워드(세전) — 인플루언서 지급 행이 PAID · NOT_APPLICABLE 이 아닌 정산의 현재 리워드 합. */
    public long sumRewardInProgress(Long creatorId) {
        Long sum = em.createQuery("SELECT COALESCE(SUM(s.rewardAmount), 0) FROM Settlement s, SettlementPayout p "
                        + "WHERE p.settlementId = s.id AND s.creator.id = :creatorId AND p.payee = :payee "
                        + "AND p.status NOT IN :settled", Long.class)
                .setParameter("creatorId", creatorId)
                .setParameter("payee", SettlementPayee.CREATOR)
                .setParameter("settled", SETTLED_PAYOUTS)
                .getSingleResult();
        return sum == null ? 0L : sum;
    }

    public long countGroupBuyPosts(Long creatorId) {
        return em.createQuery("SELECT COUNT(p) FROM GroupBuyPost p WHERE p.groupBuy.creator.id = :creatorId "
                        + "AND p.reviewStatus <> :draft", Long.class)
                .setParameter("creatorId", creatorId)
                .setParameter("draft", GroupBuyPostReviewStatus.DRAFT)
                .getSingleResult();
    }

    public long countGroupBuyEvents(Long creatorId, GroupBuyEventType eventType) {
        return em.createQuery("SELECT COUNT(h) FROM GroupBuyHistory h WHERE h.groupBuy.creator.id = :creatorId "
                        + "AND h.eventType = :eventType", Long.class)
                .setParameter("creatorId", creatorId)
                .setParameter("eventType", eventType)
                .getSingleResult();
    }

    public long countGeneralPosts(Long creatorId) {
        return em.createQuery("SELECT COUNT(p) FROM Post p WHERE p.creator.id = :creatorId AND p.postType = :type "
                        + "AND p.status IN :statuses", Long.class)
                .setParameter("creatorId", creatorId)
                .setParameter("type", PostType.GENERAL)
                .setParameter("statuses", LISTED_GENERAL_POST_STATUSES)
                .getSingleResult();
    }

    /** 브랜드 ↔ 인플루언서 1:1 스레드 — [전체, 열림]. */
    public long[] countPairThreads(Long creatorId) {
        List<MessageThread> threads = em.createQuery("SELECT t FROM MessageThread t WHERE t.connection.creator.id = :creatorId "
                        + "AND t.connection.type = :type AND t.kind = :kind", MessageThread.class)
                .setParameter("creatorId", creatorId)
                .setParameter("type", ConnectionType.PAIR)
                .setParameter("kind", ThreadKind.CONNECTION)
                .getResultList();
        long open = threads.stream().filter(t -> t.getStatus() == ThreadStatus.OPEN).count();
        return new long[]{threads.size(), open};
    }

    // ------------------------------------------------------------------ 최근 정산 · 계약 · 연결

    public long countSettlements(Long creatorId) {
        return em.createQuery("SELECT COUNT(s) FROM Settlement s WHERE s.creator.id = :creatorId", Long.class)
                .setParameter("creatorId", creatorId)
                .getSingleResult();
    }

    public List<Settlement> findRecentSettlements(Long creatorId, int size) {
        return em.createQuery("SELECT s FROM Settlement s JOIN FETCH s.market LEFT JOIN FETCH s.contract "
                        + "WHERE s.creator.id = :creatorId ORDER BY s.createdAt DESC, s.id DESC", Settlement.class)
                .setParameter("creatorId", creatorId)
                .setMaxResults(size)
                .getResultList();
    }

    public long countReceivedContracts(Long creatorId) {
        return em.createQuery("SELECT COUNT(c) FROM Contract c WHERE c.creator.id = :creatorId AND c.deletedAt IS NULL "
                        + "AND c.status IN :statuses", Long.class)
                .setParameter("creatorId", creatorId)
                .setParameter("statuses", ContractStatus.RECEIVED_BY_CREATOR)
                .getSingleResult();
    }

    /** 인플루언서에게 도착한 계약 — 브랜드 전용 상태(작성중 · 검토)는 제외(§27 설계 1-1 과 같은 집합). */
    public List<Contract> findReceivedContracts(Long creatorId, int offset, int size) {
        return em.createQuery("SELECT c FROM Contract c JOIN FETCH c.market WHERE c.creator.id = :creatorId "
                        + "AND c.deletedAt IS NULL AND c.status IN :statuses ORDER BY c.createdAt DESC, c.id DESC", Contract.class)
                .setParameter("creatorId", creatorId)
                .setParameter("statuses", ContractStatus.RECEIVED_BY_CREATOR)
                .setFirstResult(offset)
                .setMaxResults(size)
                .getResultList();
    }

    /** 공구 종료 시각 · 게시물 승인 시각 · 정산 지급 완료 시각 — 고정 지급비 「지급 시점」 도래 판정의 원천. [groupBuyId → 시각]. */
    public Map<Long, LocalDateTime> findGroupBuyEndedAt(Collection<Long> groupBuyIds) {
        return pairs("SELECT g.id, g.endedAt FROM GroupBuy g WHERE g.id IN :ids", groupBuyIds);
    }

    public Map<Long, LocalDateTime> findPostApprovedAt(Collection<Long> groupBuyIds) {
        if (groupBuyIds.isEmpty()) {
            return Map.of();
        }
        Map<Long, LocalDateTime> result = new HashMap<>();
        em.createQuery("SELECT p.groupBuy.id, p.reviewedAt FROM GroupBuyPost p WHERE p.groupBuy.id IN :ids "
                        + "AND p.reviewStatus = :approved", Object[].class)
                .setParameter("ids", groupBuyIds)
                .setParameter("approved", GroupBuyPostReviewStatus.APPROVED)
                .getResultList()
                .forEach(row -> result.put((Long) row[0], (LocalDateTime) row[1]));
        return result;
    }

    public Map<Long, LocalDateTime> findSettlementPaidAt(Collection<Long> groupBuyIds) {
        return pairs("SELECT s.groupBuy.id, s.paidAt FROM Settlement s WHERE s.groupBuy.id IN :ids", groupBuyIds);
    }

    /** 연결 브랜드 — 연결중 먼저 · 연결 종료 아래(요청중 · 거절은 「연결」이 아니다). */
    public List<Connection> findListedConnections(Long creatorId) {
        return em.createQuery("SELECT c FROM Connection c JOIN FETCH c.market WHERE c.creator.id = :creatorId "
                        + "AND c.type = :type AND c.status IN :statuses", Connection.class)
                .setParameter("creatorId", creatorId)
                .setParameter("type", ConnectionType.PAIR)
                .setParameter("statuses", LISTED_CONNECTION_STATUSES)
                .getResultList();
    }

    /** 브랜드별 판매 중 공구 수 — [marketId → 건수]. */
    public Map<Long, Long> countSellingGroupBuysByMarket(Long creatorId) {
        Map<Long, Long> result = new HashMap<>();
        em.createQuery("SELECT g.market.id, COUNT(g) FROM GroupBuy g WHERE g.creator.id = :creatorId "
                        + "AND g.status IN :statuses GROUP BY g.market.id", Object[].class)
                .setParameter("creatorId", creatorId)
                .setParameter("statuses", GroupBuyStatus.SELLING)
                .getResultList()
                .forEach(row -> result.put((Long) row[0], ((Number) row[1]).longValue()));
        return result;
    }

    private Map<Long, LocalDateTime> pairs(String jpql, Collection<Long> ids) {
        if (ids.isEmpty()) {
            return Map.of();
        }
        Map<Long, LocalDateTime> result = new HashMap<>();
        em.createQuery(jpql, Object[].class)
                .setParameter("ids", ids)
                .getResultList()
                .forEach(row -> result.put((Long) row[0], (LocalDateTime) row[1]));
        return result;
    }
}
