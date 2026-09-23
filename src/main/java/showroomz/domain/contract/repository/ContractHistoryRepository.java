package showroomz.domain.contract.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import showroomz.domain.contract.entity.ContractHistory;

import java.util.List;

/**
 * append-only. 수정·삭제 메서드를 두지 않는다(설계서 1-6) —
 * 리포지토리에 경로가 없으면 실수로 이력을 고칠 수도 없다.
 */
public interface ContractHistoryRepository extends JpaRepository<ContractHistory, Long> {

    List<ContractHistory> findByContractIdOrderByOccurredAtAscIdAsc(Long contractId);
}
