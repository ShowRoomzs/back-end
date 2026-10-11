package showroomz.domain.settlement.adjustment.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import showroomz.domain.settlement.adjustment.entity.SettlementAdjustmentProposal;
import showroomz.domain.settlement.adjustment.type.ProposalStatus;
import showroomz.domain.settlement.adjustment.type.SettlementParty;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;

/** 제안 — 응답은 전부 조건부 UPDATE({@code WHERE status IN …}) · 0행이면 409 STATE_CHANGED(44 이슈 스레드 설계서 1-4). */
public interface SettlementAdjustmentProposalRepository extends JpaRepository<SettlementAdjustmentProposal, Long> {

    @Query("SELECT p FROM SettlementAdjustmentProposal p WHERE p.adjustmentId = :adjustmentId ORDER BY p.seq ASC")
    List<SettlementAdjustmentProposal> findByAdjustmentId(@Param("adjustmentId") Long adjustmentId);

    @Query("SELECT p FROM SettlementAdjustmentProposal p WHERE p.adjustmentId IN :adjustmentIds ORDER BY p.seq ASC")
    List<SettlementAdjustmentProposal> findByAdjustmentIdIn(@Param("adjustmentIds") Collection<Long> adjustmentIds);

    /** 응답(동의 · 반대 · 다른 금액 제안으로 닫힘) — 허용된 이전 상태에서만. */
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("UPDATE SettlementAdjustmentProposal p SET p.status = :to, p.respondedAt = :now, "
            + "p.responderType = :responderType, p.responderId = :responderId "
            + "WHERE p.id = :proposalId AND p.status IN :from")
    int respond(@Param("proposalId") Long proposalId, @Param("from") Collection<ProposalStatus> from,
                @Param("to") ProposalStatus to, @Param("now") LocalDateTime now,
                @Param("responderType") SettlementParty responderType, @Param("responderId") Long responderId);

    /** 협의 만료(6-2 ③) — 열린 제안을 응답 없이 닫는다. */
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("UPDATE SettlementAdjustmentProposal p SET p.status = showroomz.domain.settlement.adjustment.type.ProposalStatus.CLOSED, "
            + "p.respondedAt = :now WHERE p.adjustmentId = :adjustmentId "
            + "AND p.status IN (showroomz.domain.settlement.adjustment.type.ProposalStatus.PENDING, "
            + "                 showroomz.domain.settlement.adjustment.type.ProposalStatus.REJECTED)")
    int closeOpen(@Param("adjustmentId") Long adjustmentId, @Param("now") LocalDateTime now);

    /** 카드 ↔ 제안 양방향 — 카드 등록 뒤 채운다. */
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("UPDATE SettlementAdjustmentProposal p SET p.cardMessageId = :messageId WHERE p.id = :proposalId")
    int linkCard(@Param("proposalId") Long proposalId, @Param("messageId") Long messageId);
}
