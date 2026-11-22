package showroomz.domain.settlement.repository;

import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import showroomz.domain.settlement.entity.SettlementPayoutGroup;
import showroomz.domain.settlement.type.PayoutGroupStatus;
import showroomz.domain.settlement.type.SettlementPayee;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

/**
 * 지급 묶음(60 이니시스 전환 계획서 D-1). 같은 묶음에 대한 동시 전송(배치 · 마감 전 틱 · 재분배 · 보류)은 {@link #findForUpdate} 행 잠금으로
 * 직렬화한다.
 */
public interface SettlementPayoutGroupRepository extends JpaRepository<SettlementPayoutGroup, Long> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT g FROM SettlementPayoutGroup g WHERE g.id = :id")
    Optional<SettlementPayoutGroup> findForUpdate(@Param("id") Long id);

    /** 같은 키의 묶음 — 차수 내림차순(맨 앞이 최신). 잠근다. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT g FROM SettlementPayoutGroup g WHERE g.provider = :provider AND g.payee = :payee AND g.payeeRefId = :refId "
            + "AND g.payDate = :payDate AND g.scheduleType = :scheduleType ORDER BY g.seq DESC")
    List<SettlementPayoutGroup> findByKeyForUpdate(@Param("provider") String provider, @Param("payee") SettlementPayee payee,
                                                   @Param("refId") Long refId, @Param("payDate") LocalDate payDate,
                                                   @Param("scheduleType") String scheduleType);

    /** 한 수취자의 열린 묶음 — 서브몰 계좌는 하나라 열린 묶음은 한 계좌를 공유해야 한다(D-1 ⑤). */
    @Query("SELECT g FROM SettlementPayoutGroup g WHERE g.provider = :provider AND g.payee = :payee AND g.payeeRefId = :refId "
            + "AND g.status = showroomz.domain.settlement.type.PayoutGroupStatus.OPEN ORDER BY g.payDate ASC, g.id ASC")
    List<SettlementPayoutGroup> findOpenForPayee(@Param("provider") String provider, @Param("payee") SettlementPayee payee,
                                                 @Param("refId") Long refId);

    /** 지급일 범위의 열린 묶음 — 수렴(D-1 ④) · 잔액 재검사(D-9). */
    @Query("SELECT g FROM SettlementPayoutGroup g WHERE g.provider = :provider "
            + "AND g.status = showroomz.domain.settlement.type.PayoutGroupStatus.OPEN "
            + "AND g.payDate >= :from AND g.payDate <= :to ORDER BY g.payDate ASC, g.id ASC")
    List<SettlementPayoutGroup> findOpenBetween(@Param("provider") String provider, @Param("from") LocalDate from,
                                                @Param("to") LocalDate to);

    /** 지급일이 지난(≤ 오늘) 열린 묶음 — 송금 결과 폴링 대상(D-8). */
    @Query("SELECT g FROM SettlementPayoutGroup g WHERE g.provider = :provider "
            + "AND g.status = showroomz.domain.settlement.type.PayoutGroupStatus.OPEN "
            + "AND g.payDate <= :today ORDER BY g.payDate ASC, g.id ASC")
    List<SettlementPayoutGroup> findOpenDue(@Param("provider") String provider, @Param("today") LocalDate today,
                                            Pageable pageable);

    long countByStatus(PayoutGroupStatus status);
}
