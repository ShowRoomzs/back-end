package showroomz.domain.contract.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import showroomz.domain.contract.entity.ContractResendRequest;

import java.util.Optional;

public interface ContractResendRequestRepository extends JpaRepository<ContractResendRequest, Long> {

    /**
     * 연타 억제(설계서 3-6). 횟수 제한 정책은 미정(§28-8 D #7)이라 만들지 않되,
     * 미처리 요청이 이미 있으면 새 행을 만들지 않아 어드민 큐에 같은 계약이 쌓이는 것만 막는다.
     */
    Optional<ContractResendRequest> findFirstByContractIdAndHandledAtIsNullOrderByRequestedAtDesc(Long contractId);
}
