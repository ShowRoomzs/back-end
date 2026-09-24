package showroomz.api.seller.contract.service;

import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import showroomz.api.seller.contract.dto.ContractClausesResponse;
import showroomz.api.seller.contract.dto.ContractDetailResponse;
import showroomz.api.seller.contract.dto.ContractDocumentDownloadResponse;
import showroomz.api.seller.contract.dto.ContractFormSourcesResponse;
import showroomz.api.seller.contract.dto.ContractListItem;
import showroomz.api.seller.contract.dto.ContractSummaryResponse;
import showroomz.domain.connection.repository.ConnectionRepository;
import showroomz.domain.contract.entity.Contract;
import showroomz.domain.contract.entity.ContractClauseVersion;
import showroomz.domain.contract.entity.ContractDocument;
import showroomz.domain.contract.repository.ContractClauseVersionRepository;
import showroomz.domain.contract.repository.ContractDocumentRepository;
import showroomz.domain.contract.repository.ContractItemRepository;
import showroomz.domain.contract.repository.ContractRepository;
import showroomz.domain.contract.service.PartyContractDocuments;
import showroomz.domain.contract.type.ContractClauseVersionStatus;
import showroomz.domain.contract.type.ContractDocumentType;
import showroomz.domain.contract.type.ContractSortType;
import showroomz.domain.contract.type.ContractStatus;
import showroomz.domain.contract.type.ContractTab;
import showroomz.domain.market.entity.Market;
import showroomz.domain.product.repository.ProductRepository;
import showroomz.global.dto.PageResponse;
import showroomz.global.dto.PagingRequest;
import showroomz.global.error.exception.BusinessException;
import showroomz.global.error.exception.ErrorCode;

import java.time.LocalDate;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 파트너센터 계약 조회(설계서 4-1).
 *
 * <p>상세 13종(B2a~B8)이 같은 응답 하나를 쓰고 화면 분기는 FE가 값으로 고른다.
 * 버튼 노출 판정({@code permissions})만은 서버가 내려준다 — FE가 복제하면 서버 허용 집합과
 * 어긋나는 버튼이 생긴다(설계서 4-5).
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class SellerContractQueryService {
    private final showroomz.api.admin.contract.service.ContractDocumentStorage contractDocumentStorage;

    private final ContractAccessGuard accessGuard;
    private final ContractRepository contractRepository;
    private final ContractItemRepository contractItemRepository;
    private final PartyContractDocuments partyContractDocuments;
    private final ContractClauseVersionRepository clauseVersionRepository;
    private final ConnectionRepository connectionRepository;
    private final ProductRepository productRepository;
    private final ContractDetailAssembler detailAssembler;

    /** 목록(A1) — 탭·검색·기간·정렬. */
    public PageResponse<ContractListItem> getContracts(String sellerEmail,
                                                       ContractTab tab,
                                                       String keyword,
                                                       LocalDate startDate,
                                                       LocalDate endDate,
                                                       ContractSortType sort,
                                                       PagingRequest pagingRequest) {
        Market market = accessGuard.resolveMarket(sellerEmail);
        Pageable pageable = PageRequest.of(
                Math.max(pagingRequest.getPage() - 1, 0), pagingRequest.getSize());

        Page<Contract> page = contractRepository.searchForSeller(
                market.getId(),
                tab == null ? ContractTab.ALL : tab,
                keyword,
                startDate,
                endDate,
                sort == null ? ContractSortType.CREATED_DESC : sort,
                pageable);

        Map<Long, Long> itemCounts = countItems(page.getContent());
        List<ContractListItem> content = page.getContent().stream()
                .map(contract -> ContractListItem.of(contract, itemCounts.getOrDefault(contract.getId(), 0L)))
                .toList();

        return PageResponse.of(new PageImpl<>(content, pageable, page.getTotalElements()));
    }

    /** 행마다 items를 지연 로딩하면 20건 목록이 21번 쿼리된다 — 한 번에 묶어 센다. */
    private Map<Long, Long> countItems(List<Contract> contracts) {
        if (contracts.isEmpty()) {
            return Map.of();
        }
        List<Long> ids = contracts.stream().map(Contract::getId).toList();
        Map<Long, Long> counts = new HashMap<>();
        for (Object[] row : contractItemRepository.countByContractIds(ids)) {
            counts.put((Long) row[0], (Long) row[1]);
        }
        return counts;
    }

    /** 탭 카운트 + GNB 배지(설계서 4-4). */
    public ContractSummaryResponse getSummary(String sellerEmail) {
        Market market = accessGuard.resolveMarket(sellerEmail);

        Map<ContractStatus, Long> byStatus = new HashMap<>();
        for (Object[] row : contractRepository.countByStatus(market.getId())) {
            byStatus.put((ContractStatus) row[0], (Long) row[1]);
        }

        // 탭 묶음은 서버가 소유한다 — FE가 9종을 6탭으로 접는 규칙을 복제하지 않게 한다(설계서 1-3).
        Map<String, Long> tabCounts = new LinkedHashMap<>();
        for (ContractTab tab : ContractTab.countable()) {
            long sum = tab.getStatuses().stream()
                    .mapToLong(status -> byStatus.getOrDefault(status, 0L))
                    .sum();
            tabCounts.put(tab.name(), sum);
        }

        return new ContractSummaryResponse(tabCounts, contractRepository.countActionRequired(market.getId()));
    }

    /** 상세(B2a~B8) — 조건·서명·상태·이력을 한 번에 내린다. */
    public ContractDetailResponse getContract(String sellerEmail, Long contractId) {
        Market market = accessGuard.resolveMarket(sellerEmail);
        Contract contract = accessGuard.loadOwned(contractId, market);
        return detailAssembler.assemble(contract);
    }

    /**
     * 계약 문서 다운로드(§25-3 #3). 체결 전에는 운영자가 받는 계약서 생성본, 체결 후에는 체결 문서 2종이다
     * ({@link PartyContractDocuments}). 지금 받을 수 없는 종류이거나 아직 없으면 404다.
     */
    public ContractDocumentDownloadResponse getDocument(String sellerEmail, Long contractId,
                                                        ContractDocumentType documentType) {
        Market market = accessGuard.resolveMarket(sellerEmail);
        Contract contract = accessGuard.loadOwned(contractId, market);
        ContractDocument document = partyContractDocuments.find(contract, documentType)
                .orElseThrow(() -> new BusinessException(ErrorCode.CONTRACT_DOCUMENT_NOT_FOUND));

        return new ContractDocumentDownloadResponse(
                document.getDocumentType(),
                document.getDocumentType().getLabel(),
                contractDocumentStorage.download(document).downloadUrl(),
                document.getOriginalName(),
                document.getSizeBytes(),
                document.getContentType(),
                document.getUploadedAt());
    }

    /**
     * 표준 조항(모달 C1·C5) — 작성 화면은 항상 <b>현행</b> 버전을 읽는다.
     * 계약이 고정한 버전은 검토 요청 이후의 계약서 기준이지 작성 폼의 기준이 아니다(설계서 1-6).
     */
    public ContractClausesResponse getClauses(String sellerEmail) {
        accessGuard.resolveMarket(sellerEmail);

        ContractClauseVersion version = clauseVersionRepository
                .findFirstByStatusOrderByEffectiveDateDescIdDesc(ContractClauseVersionStatus.EFFECTIVE)
                .orElseThrow(() -> new BusinessException(ErrorCode.CONTRACT_CLAUSE_VERSION_NOT_FOUND));

        List<ContractClausesResponse.Clause> clauses = version.getClauses().stream()
                .map(clause -> new ContractClausesResponse.Clause(
                        clause.getCode(),
                        clause.getSummaryTitle(),
                        clause.getSummaryDescription(),
                        clause.getFullTitle(),
                        clause.getFullBody()))
                .toList();

        return new ContractClausesResponse(
                version.getId(), version.getVersionNumber(), version.getEffectiveDate(), clauses);
    }

    /** 작성 폼 선택지 — 연결됨 상대 전량 + 진열 상품 전량(설계서 4-1). */
    public ContractFormSourcesResponse getFormSources(String sellerEmail) {
        Market market = accessGuard.resolveMarket(sellerEmail);

        List<ContractFormSourcesResponse.Counterparty> counterparties =
                connectionRepository.findConnectedPairsByMarketId(market.getId()).stream()
                        .map(connection -> new ContractFormSourcesResponse.Counterparty(
                                connection.getCreator().getId(),
                                connection.getCreator().getShowroomName(),
                                connection.getId(),
                                connection.getCreator().getProfileImageUrl()))
                        .toList();

        List<ContractFormSourcesResponse.ProductOption> products =
                productRepository.findDisplayedByMarketId(market.getId()).stream()
                        .map(product -> new ContractFormSourcesResponse.ProductOption(
                                product.getProductId(),
                                product.getName(),
                                product.getRegularPrice(),
                                product.getThumbnailUrl()))
                        .toList();

        return new ContractFormSourcesResponse(counterparties, products);
    }
}
