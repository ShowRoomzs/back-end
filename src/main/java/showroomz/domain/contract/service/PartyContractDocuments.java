package showroomz.domain.contract.service;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import showroomz.domain.contract.entity.Contract;
import showroomz.domain.contract.entity.ContractDocument;
import showroomz.domain.contract.repository.ContractDocumentRepository;
import showroomz.domain.contract.type.ContractDocumentType;
import showroomz.domain.contract.type.ContractStatus;

import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

/**
 * 브랜드·스튜디오가 내려받을 수 있는 계약 문서 판정 — 두 서피스가 같은 규칙을 쓴다.
 *
 * <ul>
 *   <li><b>체결 전(진행 중)</b> — 운영자가 내려받는 계약서 생성본({@code GENERATED_DRAFT}) 하나.
 *       체결 문서는 아직 모두싸인에서 받아 올리기 전이라 이 파일이 유일한 계약서다.</li>
 *   <li><b>체결완료</b> — 체결 문서 2종(서명본·감사추적인증서)만. 생성본은 서명 전 원본이라 내리지 않는다.</li>
 *   <li><b>그 외</b>(작성중·반려·종결 3종) — 없다. 성립하지 않은 계약이다.</li>
 * </ul>
 *
 * <p>생성본은 <b>지금 제출본</b>을 원본으로 만든 것만 유효하다 — 요청 취소 후 재제출하면
 * 이전 제출본의 PDF가 캐시에 남아 있어도 다른 계약서다(어드민 다운로드와 같은 판정).
 *
 * <p>스튜디오는 검토 대기 계약을 조회할 수 없으므로({@link ContractStatus#RECEIVED_BY_CREATOR})
 * 같은 집합을 써도 실제로는 서명 진행중부터 받는다.
 */
@Component
@RequiredArgsConstructor
public class PartyContractDocuments {

    private static final Set<ContractStatus> DRAFT_DOWNLOADABLE =
            Set.of(ContractStatus.REVIEW_PENDING, ContractStatus.SIGNING, ContractStatus.CONCLUSION_PENDING);

    private final ContractDocumentRepository contractDocumentRepository;

    /** 상세 문서 목록 — 종류 선언 순서. */
    public List<ContractDocument> list(Contract contract) {
        return contractDocumentRepository.findByContractIdInTypeOrder(contract.getId()).stream()
                .filter(document -> downloadable(contract, document))
                .toList();
    }

    /** 단건 다운로드 — 지금 받을 수 없는 종류이거나 아직 없으면 비어 있다. */
    public Optional<ContractDocument> find(Contract contract, ContractDocumentType documentType) {
        return contractDocumentRepository.findByContractIdAndDocumentType(contract.getId(), documentType)
                .filter(document -> downloadable(contract, document));
    }

    private boolean downloadable(Contract contract, ContractDocument document) {
        ContractStatus status = contract.getStatus();
        if (document.getDocumentType() == ContractDocumentType.GENERATED_DRAFT) {
            return DRAFT_DOWNLOADABLE.contains(status)
                    && Objects.equals(document.getSourceReviewRequestedAt(), contract.getReviewRequestedAt());
        }
        return status == ContractStatus.CONCLUDED;
    }
}
