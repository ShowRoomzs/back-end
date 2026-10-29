package showroomz.domain.contract.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import showroomz.domain.contract.entity.ContractTerminationRequest;
import showroomz.domain.contract.type.ContractTerminationRequestStatus;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;

public interface ContractTerminationRequestRepository extends JpaRepository<ContractTerminationRequest, Long> {

    Optional<ContractTerminationRequest> findFirstByContract_IdAndStatusOrderByRequestedAtDesc(
            Long contractId, ContractTerminationRequestStatus status);

    default Optional<ContractTerminationRequest> findPending(Long contractId) {
        return findFirstByContract_IdAndStatusOrderByRequestedAtDesc(contractId, ContractTerminationRequestStatus.PENDING);
    }

    /** 가장 최근 요청(상태 무관) — 해지 상세(B9)의 「사유 구분 · 메모」. */
    Optional<ContractTerminationRequest> findFirstByContract_IdOrderByRequestedAtDescIdDesc(Long contractId);

    List<ContractTerminationRequest> findByContract_IdInAndStatus(Collection<Long> contractIds,
                                                                 ContractTerminationRequestStatus status);

    default Map<Long, ContractTerminationRequest> findPendingByContractIds(Collection<Long> contractIds) {
        if (contractIds.isEmpty()) {
            return Map.of();
        }
        return findByContract_IdInAndStatus(contractIds, ContractTerminationRequestStatus.PENDING).stream()
                .collect(Collectors.toMap(r -> r.getContract().getId(), Function.identity(), (a, b) -> a));
    }

    List<ContractTerminationRequest> findByContract_IdIn(Collection<Long> contractIds);

    /** 계약별 가장 최근 요청(상태 무관) — 파트너 공구 목록(58 계획 6-3). 계약당 요청은 몇 건이라 메모리에서 고른다. */
    default Map<Long, ContractTerminationRequest> findLatestByContractIds(Collection<Long> contractIds) {
        if (contractIds.isEmpty()) {
            return Map.of();
        }
        return findByContract_IdIn(contractIds).stream().collect(Collectors.toMap(r -> r.getContract().getId(),
                Function.identity(), (a, b) -> java.util.Comparator.comparing(ContractTerminationRequest::getRequestedAt)
                        .thenComparing(ContractTerminationRequest::getId).compare(a, b) >= 0 ? a : b));
    }

    long countByStatus(ContractTerminationRequestStatus status);

    /** 공구가 열리면 해지 요청은 닫힌다 — 오픈 뒤는 공구 중단 요청으로(파트너 08 C9 안내). @return 닫은 요청 수 */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("UPDATE ContractTerminationRequest r SET r.status = showroomz.domain.contract.type.ContractTerminationRequestStatus.LAPSED, "
            + "r.closedAt = :now WHERE r.groupBuyId = :groupBuyId "
            + "AND r.status = showroomz.domain.contract.type.ContractTerminationRequestStatus.PENDING")
    int lapseByGroupBuy(@Param("groupBuyId") Long groupBuyId, @Param("now") LocalDateTime now);
}
