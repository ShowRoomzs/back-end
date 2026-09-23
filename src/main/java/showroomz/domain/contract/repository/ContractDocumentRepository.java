package showroomz.domain.contract.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import showroomz.domain.contract.entity.ContractDocument;
import showroomz.domain.contract.type.ContractDocumentType;

import java.util.List;
import java.util.Optional;

public interface ContractDocumentRepository extends JpaRepository<ContractDocument, Long> {

    List<ContractDocument> findByContractIdOrderByDocumentTypeAsc(Long contractId);

    Optional<ContractDocument> findByContractIdAndDocumentType(Long contractId, ContractDocumentType documentType);
}
