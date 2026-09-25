package showroomz.domain.groupbuy.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import showroomz.domain.groupbuy.entity.GroupBuyAdminSuspension;
import showroomz.domain.groupbuy.type.AdminSuspensionStatus;
import showroomz.domain.groupbuy.type.SuspensionWithdrawReason;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface GroupBuyAdminSuspensionRepository extends JpaRepository<GroupBuyAdminSuspension, Long> {

    List<GroupBuyAdminSuspension> findByGroupBuyIdOrderByNoticedAtDescIdDesc(Long groupBuyId);

    Optional<GroupBuyAdminSuspension> findFirstByGroupBuyIdAndStatus(Long groupBuyId, AdminSuspensionStatus status);

    /** 목록 비고 조립 — 진행 중 통지를 페이지 단위로 한 번에 모은다. */
    @Query("SELECT s FROM GroupBuyAdminSuspension s "
            + "WHERE s.groupBuy.id IN :groupBuyIds "
            + "AND s.status = showroomz.domain.groupbuy.type.AdminSuspensionStatus.NOTICED")
    List<GroupBuyAdminSuspension> findNoticedByGroupBuyIds(@Param("groupBuyIds") Collection<Long> groupBuyIds);

    /** GNB 배지 ② — 소명 가능(B4i). */
    @Query("SELECT COUNT(s) FROM GroupBuyAdminSuspension s "
            + "WHERE s.groupBuy.market.id = :marketId "
            + "AND s.status = showroomz.domain.groupbuy.type.AdminSuspensionStatus.NOTICED "
            + "AND s.appealSubmittedAt IS NULL "
            + "AND s.appealDeadlineAt IS NOT NULL AND s.appealDeadlineAt >= :now")
    long countAppealable(@Param("marketId") Long marketId, @Param("now") LocalDateTime now);

    /**
     * 소명 제출(설계서 4-6) — 제출 후 수정 불가를 조건부 UPDATE로 막는다. 0행이면 서비스가
     * 미제출·기한·통지 상태를 다시 읽어 409 사유를 구분한다.
     *
     * <p>clearAutomatically는 쓰지 않는다 — 호출자가 잠가 둔 공구 엔티티가 준영속으로 떨어져 이어지는
     * 상세 조립이 지연 로딩에 실패한다. 호출자는 성공 후 엔티티에도 같은 값을 반영한다.
     */
    @Modifying(flushAutomatically = true)
    @Query("UPDATE GroupBuyAdminSuspension s "
            + "SET s.appealContent = :content, s.appealSubmittedAt = :now, s.appealSubmittedBy = :sellerId "
            + "WHERE s.id = :suspensionId "
            + "AND s.status = showroomz.domain.groupbuy.type.AdminSuspensionStatus.NOTICED "
            + "AND s.appealSubmittedAt IS NULL "
            + "AND s.appealDeadlineAt >= :now")
    int submitAppeal(@Param("suspensionId") Long suspensionId,
                     @Param("content") String content,
                     @Param("sellerId") Long sellerId,
                     @Param("now") LocalDateTime now);

    /**
     * 철회(M7) — NOTICED일 때만. 집행·철회를 운영자 둘이 동시에 누르면 한쪽이 0행이다(32 설계 9-1).
     * 소명 제출도 NOTICED 조건부라 철회가 먼저면 소명이 409가 된다.
     */
    @Modifying(flushAutomatically = true)
    @Query("UPDATE GroupBuyAdminSuspension s "
            + "SET s.status = showroomz.domain.groupbuy.type.AdminSuspensionStatus.WITHDRAWN, "
            + "    s.withdrawReasonCode = :reasonCode, s.withdrawDetail = :detail, "
            + "    s.withdrawnAt = :now, s.withdrawnBy = :operatorId "
            + "WHERE s.id = :suspensionId "
            + "AND s.status = showroomz.domain.groupbuy.type.AdminSuspensionStatus.NOTICED")
    int withdraw(@Param("suspensionId") Long suspensionId,
                 @Param("reasonCode") SuspensionWithdrawReason reasonCode,
                 @Param("detail") String detail,
                 @Param("operatorId") Long operatorId,
                 @Param("now") LocalDateTime now);

    /** 집행(32 설계 6-3) — NOTICED ∧ 집행 시각 도달일 때만. 시각 조건을 UPDATE가 다시 본다. */
    @Modifying(flushAutomatically = true)
    @Query("UPDATE GroupBuyAdminSuspension s "
            + "SET s.status = showroomz.domain.groupbuy.type.AdminSuspensionStatus.EXECUTED, "
            + "    s.executionNote = :note, s.executedAt = :now, s.executedBy = :operatorId "
            + "WHERE s.id = :suspensionId "
            + "AND s.status = showroomz.domain.groupbuy.type.AdminSuspensionStatus.NOTICED "
            + "AND s.appealDeadlineAt < :now AND s.executeScheduledAt <= :now")
    int execute(@Param("suspensionId") Long suspensionId,
                @Param("note") String note,
                @Param("operatorId") Long operatorId,
                @Param("now") LocalDateTime now);
}
