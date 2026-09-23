package showroomz.domain.contract.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import showroomz.domain.contract.entity.ContractDocument;
import showroomz.domain.contract.type.ContractDocumentType;

import java.util.Comparator;
import java.util.List;
import java.util.Optional;

public interface ContractDocumentRepository extends JpaRepository<ContractDocument, Long> {

    List<ContractDocument> findByContractId(Long contractId);

    /**
     * 문서 종류의 <b>선언 순서</b>(생성본 → 서명 완료 계약서 → 감사추적인증서)로 돌려준다 —
     * 스튜디오 S6·파트너 문서 카드가 이 순서로 그린다.
     *
     * <p>DB에서 정렬하지 않는다. 컬럼이 {@code EnumType.STRING}이라 {@code ORDER BY document_type}은
     * 알파벳순(AUDIT_TRAIL &lt; SIGNED_PDF)이 되어 계약의 원본보다 그 서명 이력이 먼저 선다.
     * 한 계약의 문서는 많아야 3건이다.
     */
    default List<ContractDocument> findByContractIdInTypeOrder(Long contractId) {
        return findByContractId(contractId).stream()
                .sorted(Comparator.comparing(ContractDocument::getDocumentType))
                .toList();
    }

    Optional<ContractDocument> findByContractIdAndDocumentType(Long contractId, ContractDocumentType documentType);
}
