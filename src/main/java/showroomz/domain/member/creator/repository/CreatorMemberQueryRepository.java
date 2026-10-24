package showroomz.domain.member.creator.repository;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.springframework.stereotype.Repository;
import showroomz.domain.connection.entity.Connection;
import showroomz.domain.connection.type.ConnectionStatus;
import showroomz.domain.connection.type.ConnectionType;
import showroomz.domain.contract.type.ContractStatus;
import showroomz.domain.groupbuy.type.GroupBuyStatus;
import showroomz.domain.settlement.type.SettlementStatus;

import java.util.List;
import java.util.Set;

/**
 * 인플루언서 탈퇴 조건 판정의 원천 쿼리(53 설계서 4-5) — 공구 · 계약 · 정산 리포지토리에 메서드를 더하지 않고 여기에 모은다(판정식이
 * 한 곳에 보이게 · 다른 모듈의 리포지토리를 넓히지 않게).
 */
@Repository
public class CreatorMemberQueryRepository {

    /** 체결 전 · 서명 대기 — 끝나지 않은 계약. 체결완료는 연결 공구 조건(①)이 센다. 브랜드 전용 상태는 인플루언서 조건이 아니다. */
    public static final Set<ContractStatus> OPEN_CONTRACT_STATUSES =
            Set.of(ContractStatus.SIGNING, ContractStatus.CONCLUSION_PENDING);

    /** 종결됐지만 주문이 남았을 수 있는 공구 — 정산이 아직 없으면 미종결 주문을 센다. */
    public static final Set<GroupBuyStatus> CLOSED_GROUP_BUY_STATUSES =
            Set.of(GroupBuyStatus.ENDED, GroupBuyStatus.SUSPENDED);

    @PersistenceContext
    private EntityManager em;

    public long countActiveGroupBuys(Long creatorId) {
        return em.createQuery("SELECT COUNT(g) FROM GroupBuy g WHERE g.creator.id = :creatorId "
                        + "AND g.status IN :statuses", Long.class)
                .setParameter("creatorId", creatorId)
                .setParameter("statuses", GroupBuyStatus.ACTIVE)
                .getSingleResult();
    }

    /** 종료 · 중단 공구 중 정산이 생성되지 않은 것 — 「종료 후 주문 종결 대기」 후보. */
    public List<Long> findClosedGroupBuyIdsWithoutSettlement(Long creatorId) {
        return em.createQuery("SELECT g.id FROM GroupBuy g WHERE g.creator.id = :creatorId AND g.status IN :statuses "
                        + "AND NOT EXISTS (SELECT s.id FROM Settlement s WHERE s.groupBuy.id = g.id)", Long.class)
                .setParameter("creatorId", creatorId)
                .setParameter("statuses", CLOSED_GROUP_BUY_STATUSES)
                .getResultList();
    }

    public long countOpenContracts(Long creatorId) {
        return em.createQuery("SELECT COUNT(c) FROM Contract c WHERE c.creator.id = :creatorId "
                        + "AND c.deletedAt IS NULL AND c.status IN :statuses", Long.class)
                .setParameter("creatorId", creatorId)
                .setParameter("statuses", OPEN_CONTRACT_STATUSES)
                .getSingleResult();
    }

    // ------------------------------------------------------------------ 스튜디오 조건 내역(50 설계서 4-1 · 표시 전용)

    /** 진행 중 공구의 상태별 건수 — [status, count]. 합은 {@link #countActiveGroupBuys}와 같다. */
    public List<Object[]> countActiveGroupBuysByStatus(Long creatorId) {
        return em.createQuery("SELECT g.status, COUNT(g) FROM GroupBuy g WHERE g.creator.id = :creatorId "
                        + "AND g.status IN :statuses GROUP BY g.status", Object[].class)
                .setParameter("creatorId", creatorId)
                .setParameter("statuses", GroupBuyStatus.ACTIVE)
                .getResultList();
    }

    /** 끝나지 않은 계약의 상태별 건수 — [status, count]. 합은 {@link #countOpenContracts}와 같다. */
    public List<Object[]> countOpenContractsByStatus(Long creatorId) {
        return em.createQuery("SELECT c.status, COUNT(c) FROM Contract c WHERE c.creator.id = :creatorId "
                        + "AND c.deletedAt IS NULL AND c.status IN :statuses GROUP BY c.status", Object[].class)
                .setParameter("creatorId", creatorId)
                .setParameter("statuses", OPEN_CONTRACT_STATUSES)
                .getResultList();
    }

    /**
     * 체결완료 계약 중 연결 공구(1:1)가 끝나지 않은 것 — 진행 중 공구 + 「종료 후 주문 종결 대기」 공구({@code awaitingIds}). 판정식은 이
     * 계약을 세지 않는다(①이 그 공구를 센다) — 화면 내역에만 쓴다.
     */
    public long countConcludedContractsOfUnfinishedGroupBuys(Long creatorId, List<Long> awaitingIds) {
        long active = em.createQuery("SELECT COUNT(g) FROM GroupBuy g WHERE g.creator.id = :creatorId "
                        + "AND g.status IN :statuses AND g.contract.status = :concluded AND g.contract.deletedAt IS NULL", Long.class)
                .setParameter("creatorId", creatorId)
                .setParameter("statuses", GroupBuyStatus.ACTIVE)
                .setParameter("concluded", ContractStatus.CONCLUDED)
                .getSingleResult();
        if (awaitingIds.isEmpty()) {
            return active;
        }
        long awaiting = em.createQuery("SELECT COUNT(g) FROM GroupBuy g WHERE g.id IN :ids "
                        + "AND g.contract.status = :concluded AND g.contract.deletedAt IS NULL", Long.class)
                .setParameter("ids", awaitingIds)
                .setParameter("concluded", ContractStatus.CONCLUDED)
                .getSingleResult();
        return active + awaiting;
    }

    /** 지급 완료가 아닌 정산의 상태별 건수 — [status, count]. 합은 {@link #countUnsettled}와 같다. */
    public List<Object[]> countUnsettledByStatus(Long creatorId) {
        return em.createQuery("SELECT s.status, COUNT(s) FROM Settlement s WHERE s.creator.id = :creatorId "
                        + "AND s.status <> :paid GROUP BY s.status", Object[].class)
                .setParameter("creatorId", creatorId)
                .setParameter("paid", SettlementStatus.PAID)
                .getResultList();
    }

    /** 지급 완료가 아닌 정산 — 정산 확인 중 · 조정 협의 · 지급 예정 · 분배 실패. */
    public long countUnsettled(Long creatorId) {
        return em.createQuery("SELECT COUNT(s) FROM Settlement s WHERE s.creator.id = :creatorId "
                        + "AND s.status <> :paid", Long.class)
                .setParameter("creatorId", creatorId)
                .setParameter("paid", SettlementStatus.PAID)
                .getSingleResult();
    }

    /** 탈퇴 승인의 연결 일괄 종료 대상 — 연결중 · 요청중(53 설계서 4-4 ⑤). */
    public List<Connection> findOpenPairConnections(Long creatorId) {
        return em.createQuery("SELECT c FROM Connection c WHERE c.creator.id = :creatorId AND c.type = :type "
                        + "AND c.status IN :statuses", Connection.class)
                .setParameter("creatorId", creatorId)
                .setParameter("type", ConnectionType.PAIR)
                .setParameter("statuses", Set.of(ConnectionStatus.CONNECTED, ConnectionStatus.REQUESTED))
                .getResultList();
    }
}
