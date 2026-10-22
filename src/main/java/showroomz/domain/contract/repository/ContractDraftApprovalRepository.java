package showroomz.domain.contract.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import showroomz.domain.contract.entity.ContractDraftApproval;

import java.util.List;
import java.util.Optional;

public interface ContractDraftApprovalRepository extends JpaRepository<ContractDraftApproval, Long> {

    List<ContractDraftApproval> findByContractIdAndDeletedAtIsNullOrderByApprovedAtAscIdAsc(Long contractId);

    Optional<ContractDraftApproval> findByIdAndContractIdAndDeletedAtIsNull(Long id, Long contractId);
}
