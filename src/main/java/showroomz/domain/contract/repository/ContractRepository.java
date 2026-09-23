package showroomz.domain.contract.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import showroomz.domain.contract.entity.Contract;
import showroomz.domain.contract.type.ContractStatus;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface ContractRepository extends JpaRepository<Contract, Long>, ContractRepositoryCustom {

    /** 문서 변경과 체결 검증도 동일한 계약 행을 잠가 원자적으로 처리한다. */
    @org.springframework.data.jpa.repository.Lock(jakarta.persistence.LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT c FROM Contract c WHERE c.id = :contractId")
    Optional<Contract> findForAdminUpdate(@Param("contractId") Long contractId);

    @Query("SELECT c.status, COUNT(c) FROM Contract c WHERE c.status <> showroomz.domain.contract.type.ContractStatus.DRAFT GROUP BY c.status")
    List<Object[]> countAdminStatuses();

    @Query("SELECT c FROM Contract c "
            + "LEFT JOIN FETCH c.creator cr "
            + "LEFT JOIN FETCH c.market m "
            + "WHERE c.id = :contractId")
    Optional<Contract> findDetailById(@Param("contractId") Long contractId);

    /**
     * 상태 전이의 경합 차단(설계서 3-3). 읽고-판정하고-저장하는 사이에 다른 요청이 상태를 바꿔도
     * DB가 한 번 더 막는다 — 브랜드의 [요청 취소]와 어드민의 [검토 통과]가 동시에 들어오면
     * 하나는 반드시 0행이 되어야 한다. 둘 다 통과하면 「작성중인데 서명 요청이 발송된 계약」이 생긴다.
     *
     * <p>status 컬럼만 건드리고 version은 올리지 않는다 — 전이에 딸린 나머지 필드는 같은 트랜잭션에서
     * 엔티티 변경 감지로 쓰이고, 그때 낙관적 락이 정상적으로 동작해야 한다.
     */
    @Modifying(flushAutomatically = true)
    @Query("UPDATE Contract c SET c.status = :to WHERE c.id = :contractId AND c.status = :from")
    int transitionStatus(@Param("contractId") Long contractId,
                         @Param("from") ContractStatus from,
                         @Param("to") ContractStatus to);

    /** 검토 요청·재요청은 출발 상태가 둘(DRAFT · REVIEW_REJECTED)이라 IN으로 받는다. */
    @Modifying(flushAutomatically = true)
    @Query("UPDATE Contract c SET c.status = :to WHERE c.id = :contractId AND c.status IN :from")
    int transitionStatusFromAny(@Param("contractId") Long contractId,
                                @Param("from") List<ContractStatus> from,
                                @Param("to") ContractStatus to);

    /**
     * 고정 지급비 [지급 완료 기록](B5a). 0행이면 이미 기록됐거나 체결완료가 아니다.
     * 되돌리는 경로는 만들지 않는다(설계서 미결 #4).
     */
    @Modifying(flushAutomatically = true)
    @Query("UPDATE Contract c SET c.fixedFeePaidAt = :now "
            + "WHERE c.id = :contractId AND c.status = showroomz.domain.contract.type.ContractStatus.CONCLUDED "
            + "AND c.fixedFeeAmount > 0 AND c.fixedFeePaidAt IS NULL")
    int markFixedFeePaid(@Param("contractId") Long contractId, @Param("now") LocalDateTime now);

    /**
     * 공구 생성 게이트(설계서 1-8). 계약 1건 = 공구 1건.
     * 0행이면 체결완료가 아니거나 이미 공구가 생성된 계약이다 —
     * B5a의 "공구가 이미 생성돼 있어 추가로 만들 수 없습니다"가 이 한 줄로 성립한다.
     */
    @Modifying(flushAutomatically = true)
    @Query("UPDATE Contract c SET c.groupBuyId = :groupBuyId "
            + "WHERE c.id = :contractId AND c.status = showroomz.domain.contract.type.ContractStatus.CONCLUDED "
            + "AND c.groupBuyId IS NULL")
    int assignGroupBuy(@Param("contractId") Long contractId, @Param("groupBuyId") Long groupBuyId);

    /** 탭 카운트(설계서 4-4). 탭 묶음은 서버가 소유하므로 상태별 카운트를 받아 서비스가 묶는다. */
    @Query("SELECT c.status, COUNT(c) FROM Contract c WHERE c.market.id = :marketId GROUP BY c.status")
    List<Object[]> countByStatus(@Param("marketId") Long marketId);

    /**
     * GNB 배지 — 브랜드가 지금 조치해야 하는 건수(설계서 4-4).
     * 검토 반려(브랜드 조치로만 풀리는 유일한 진행 상태)와 B4c(상대만 서명 완료)만 센다.
     * 검토 대기·체결 처리 대기·B4는 공이 상대에게 있는 정상 대기라 넣지 않는다.
     */
    @Query("SELECT COUNT(c) FROM Contract c WHERE c.market.id = :marketId AND ("
            + "  c.status = showroomz.domain.contract.type.ContractStatus.REVIEW_REJECTED"
            + "  OR (c.status = showroomz.domain.contract.type.ContractStatus.SIGNING"
            + "      AND c.creatorSignedAt IS NOT NULL AND c.brandSignedAt IS NULL))")
    long countActionRequired(@Param("marketId") Long marketId);
}
