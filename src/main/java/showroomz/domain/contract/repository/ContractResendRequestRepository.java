package showroomz.domain.contract.repository;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import showroomz.domain.contract.entity.ContractResendRequest;
import showroomz.domain.contract.type.ContractActorType;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface ContractResendRequestRepository extends JpaRepository<ContractResendRequest, Long> {

    java.util.List<ContractResendRequest> findByContractIdOrderByRequestedAtDescIdDesc(Long contractId);

    @org.springframework.data.jpa.repository.Modifying
    @org.springframework.data.jpa.repository.Query("UPDATE ContractResendRequest r SET r.handledAt = :now, r.handledBy = :operator WHERE r.contract.id = :id AND r.handledAt IS NULL")
    int handleAll(@org.springframework.data.repository.query.Param("id") Long id,
                  @org.springframework.data.repository.query.Param("operator") Long operator,
                  @org.springframework.data.repository.query.Param("now") java.time.LocalDateTime now);

    /**
     * 연타 억제(설계서 3-6) — <b>요청자별</b>이다(36 설계 5-1). 요청 카드가 요청자의 운영팀 채널에 붙으므로,
     * 계약 단위로 막으면 브랜드가 먼저 요청한 계약에서 인플루언서 채널에 카드가 생기지 않는다.
     * 횟수 제한 정책은 미정(§28-8 D #7)이라 만들지 않는다 — 같은 요청자의 미처리 요청이 있으면 그 요청을 돌려준다.
     */
    Optional<ContractResendRequest> findFirstByContractIdAndRequesterTypeAndHandledAtIsNullOrderByRequestedAtDesc(
            Long contractId, ContractActorType requesterType);

    /**
     * 재발송 완료 알림의 잠금(36 설계 5-2) — 알림 전송과 카드 상태 전환이 한 번에 일어나야 한다.
     * 운영자 둘이 동시에 누르면 뒤쪽은 잠금이 풀린 뒤 이미 처리된 행을 본다.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT r FROM ContractResendRequest r WHERE r.id = :id")
    Optional<ContractResendRequest> findForNotice(@Param("id") Long id);

    /** 카드의 액션 상태 — 페이지에 있는 카드들의 요청과 계약 상태를 한 번에 읽는다(36 설계 4-2). */
    @Query("SELECT r FROM ContractResendRequest r JOIN FETCH r.contract WHERE r.id IN :ids")
    List<ContractResendRequest> findWithContractByIdIn(@Param("ids") Collection<Long> ids);

    /**
     * 탭 배지의 미처리 카드 수(36 설계 3-2) — 요청자 유형별. 카드가 있고 · 알림 전이고 · 계약이 서명 진행중인 것만 센다.
     * 계약 관리의 재발송 큐와 같은 정의에 「카드가 있다」만 더했다.
     */
    @Query("SELECT r.requesterType, COUNT(r) FROM ContractResendRequest r "
            + "WHERE r.handledAt IS NULL AND r.cardMessageId IS NOT NULL "
            + "AND r.contract.status = showroomz.domain.contract.type.ContractStatus.SIGNING "
            + "GROUP BY r.requesterType")
    List<Object[]> countPendingCardsByRequesterType();
}
