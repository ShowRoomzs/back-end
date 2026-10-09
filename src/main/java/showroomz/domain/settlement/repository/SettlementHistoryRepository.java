package showroomz.domain.settlement.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import showroomz.domain.settlement.entity.SettlementHistory;
import showroomz.domain.settlement.type.SettlementEventType;

import java.util.Collection;
import java.util.List;

public interface SettlementHistoryRepository extends JpaRepository<SettlementHistory, Long> {

    /** 07b 처리 이력 — 최신순. */
    @Query("SELECT h FROM SettlementHistory h WHERE h.settlementId = :settlementId ORDER BY h.occurredAt DESC, h.id DESC")
    List<SettlementHistory> findLatestFirst(@Param("settlementId") Long settlementId);

    /** 마지막 사건 하나 — 확정 근거 문장(파트너 {@code payoutBasis})은 확정 이력 detail 이다. */
    @Query("SELECT h FROM SettlementHistory h WHERE h.settlementId = :settlementId AND h.eventType IN :types "
            + "ORDER BY h.occurredAt DESC, h.id DESC")
    List<SettlementHistory> findByTypesLatestFirst(@Param("settlementId") Long settlementId,
                                                   @Param("types") Collection<SettlementEventType> types);

    long countBySettlementIdAndEventType(Long settlementId, SettlementEventType eventType);
}
