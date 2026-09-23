package showroomz.api.creator.contract.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import showroomz.api.admin.contract.service.ContractDocumentStorage;
import showroomz.api.creator.contract.dto.CreatorContractClausesResponse;
import showroomz.api.creator.contract.dto.CreatorContractDetailResponse;
import showroomz.api.creator.contract.dto.CreatorContractDocumentDownloadResponse;
import showroomz.api.creator.contract.dto.CreatorContractListItem;
import showroomz.api.creator.contract.dto.CreatorContractSummaryResponse;
import showroomz.domain.contract.entity.Contract;
import showroomz.domain.contract.entity.ContractClauseVersion;
import showroomz.domain.contract.entity.ContractDocument;
import showroomz.domain.contract.repository.ContractItemRepository;
import showroomz.domain.contract.repository.ContractRepository;
import showroomz.domain.contract.type.ContractDocumentType;
import showroomz.domain.contract.type.ContractStatus;
import showroomz.domain.contract.type.CreatorContractSortType;
import showroomz.domain.contract.type.CreatorContractTab;
import showroomz.domain.member.creator.entity.Creator;
import showroomz.global.dto.PageResponse;
import showroomz.global.dto.PagingRequest;
import showroomz.global.error.exception.BusinessException;
import showroomz.global.error.exception.ErrorCode;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 쇼룸 스튜디오 계약 조회(§27).
 *
 * <p>스튜디오는 <b>읽기 서피스</b>다 — 인플루언서가 계약 객체에 쓸 수 있는 값은 거절 · 재발송 요청 ·
 * 열람 기록 셋뿐이고, 그래서 이 설계서에는 하드 검증도 경고도 없다. 검증할 입력값이 없다(설계서 0-1).
 *
 * <p>모든 경로가 {@link CreatorContractReader}의 가시성 판정을 통과한다. 이것을 서비스마다
 * 베껴 쓰거나 한 곳이라도 빠뜨리면 브랜드가 아직 보내지도 않은 계약이 나간다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class CreatorContractQueryService {

    private final CreatorContractReader reader;
    private final ContractRepository contractRepository;
    private final ContractItemRepository contractItemRepository;
    private final CreatorContractDetailAssembler detailAssembler;
    private final CreatorContractViewRecorder viewRecorder;
    private final ContractDocumentStorage contractDocumentStorage;

    /** 목록(S1) — 탭 · 검색 · 정렬. 빈 상태(S2)는 {@code totalCount = 0}으로 FE가 그린다. */
    public PageResponse<CreatorContractListItem> getContracts(String creatorEmail,
                                                              CreatorContractTab tab,
                                                              String keyword,
                                                              CreatorContractSortType sort,
                                                              PagingRequest pagingRequest) {
        Creator creator = reader.resolveCreator(creatorEmail);
        Pageable pageable = PageRequest.of(
                Math.max(pagingRequest.getPage() - 1, 0), pagingRequest.getSize());

        Page<Contract> page = contractRepository.searchForCreator(
                creator.getId(),
                tab == null ? CreatorContractTab.ALL : tab,
                keyword,
                sort == null ? CreatorContractSortType.RECEIVED_DESC : sort,
                pageable);

        // 기한 임박 판정은 「지금」 기준이다. 행마다 now()를 새로 읽으면 한 페이지 안에서
        // 경계에 걸친 두 행이 서로 다른 기준을 보게 되므로 한 번만 읽어 넘긴다.
        LocalDateTime now = LocalDateTime.now();
        Map<Long, Long> itemCounts = countItems(page.getContent());
        List<CreatorContractListItem> content = page.getContent().stream()
                .map(contract -> CreatorContractListItem.of(
                        contract, itemCounts.getOrDefault(contract.getId(), 0L), now))
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

    /** 탭 카운트 + GNB 배지(설계서 3). */
    public CreatorContractSummaryResponse getSummary(String creatorEmail) {
        Creator creator = reader.resolveCreator(creatorEmail);

        Map<ContractStatus, Long> byStatus = new HashMap<>();
        for (Object[] row : contractRepository.countByStatusForCreator(
                creator.getId(), ContractStatus.RECEIVED_BY_CREATOR)) {
            byStatus.put((ContractStatus) row[0], (Long) row[1]);
        }

        // 탭 묶음은 서버가 소유한다 — 작성중 탭은 키 자체가 없다(§27-1 #1).
        Map<String, Long> tabCounts = new LinkedHashMap<>();
        for (CreatorContractTab tab : CreatorContractTab.countable()) {
            long sum = tab.getStatuses().stream()
                    .mapToLong(status -> byStatus.getOrDefault(status, 0L))
                    .sum();
            tabCounts.put(tab.name(), sum);
        }

        return new CreatorContractSummaryResponse(
                tabCounts, contractRepository.countCreatorActionRequired(creator.getId()));
    }

    /**
     * 상세(S3 · S3a · S3b · S3c · S6 · S7 · S8 · S9).
     *
     * <p>{@code tab}·{@code keyword}·{@code sort}는 <b>선택</b>이다. 헤더의 [‹ 이전] [다음 ›]은
     * 현재 목록의 정렬·필터 안에서의 이웃이라(설계서 2-5) 목록 조건을 모르면 계산할 수 없다.
     * 없으면 두 값은 null이고 FE는 버튼을 비활성한다.
     *
     * <p>최초 진입이면 열람 시각을 찍는다 — GET의 부수 효과이고, 실패해도 조회를 깨지 않는다.
     */
    public CreatorContractDetailResponse getContract(String creatorEmail,
                                                     Long contractId,
                                                     CreatorContractTab tab,
                                                     String keyword,
                                                     CreatorContractSortType sort) {
        Creator creator = reader.resolveCreator(creatorEmail);
        Contract contract = reader.requireReceived(creator.getId(), contractId);

        CreatorContractDetailResponse.Navigation navigation =
                navigation(creator.getId(), contract, tab, keyword, sort);
        CreatorContractDetailResponse response = detailAssembler.assemble(contract, navigation);

        recordFirstViewQuietly(contract);
        return response;
    }

    /**
     * 열람 기록은 <b>응답을 조립한 뒤</b> 찍고 실패를 삼킨다(설계서 5-3).
     * 예외를 별도 트랜잭션 안에서 잡으면 커밋 시점에 다시 터지므로 삼키는 자리는 여기다.
     */
    private void recordFirstViewQuietly(Contract contract) {
        if (contract.getCreatorViewedAt() != null) {
            return;
        }
        try {
            viewRecorder.recordFirstView(contract.getId());
        } catch (RuntimeException e) {
            log.warn("[contract] creator view stamp failed. contractId={}", contract.getId(), e);
        }
    }

    /**
     * 이웃 2건. 목록 전체를 다시 조회하지 않는다 — 정렬 키 기준 앞뒤 1건씩만 뽑는 쿼리 두 방이다.
     * 목록 조건이 하나도 오지 않았으면 이웃을 계산할 근거가 없으므로 둘 다 null이다.
     */
    private CreatorContractDetailResponse.Navigation navigation(Long creatorId,
                                                                Contract contract,
                                                                CreatorContractTab tab,
                                                                String keyword,
                                                                CreatorContractSortType sort) {
        boolean hasListContext = tab != null || sort != null || (keyword != null && !keyword.isBlank());
        if (!hasListContext) {
            return new CreatorContractDetailResponse.Navigation(null, null);
        }

        CreatorContractTab effectiveTab = tab == null ? CreatorContractTab.ALL : tab;
        CreatorContractSortType effectiveSort = sort == null ? CreatorContractSortType.RECEIVED_DESC : sort;

        return new CreatorContractDetailResponse.Navigation(
                contractRepository.findNeighborForCreator(
                        creatorId, contract, effectiveTab, keyword, effectiveSort, false),
                contractRepository.findNeighborForCreator(
                        creatorId, contract, effectiveTab, keyword, effectiveSort, true));
    }

    /**
     * 표준 조항 — <b>계약에 고정된 버전</b>을 읽는다(설계서 6-3).
     *
     * <p>파트너처럼 현행 버전을 읽지 않는다. 문안이 개정된 뒤에 <b>내가 서명한 계약의 조항과
     * 화면에 뜨는 조항이 달라지면 안 된다.</b> 스튜디오가 보는 계약은 전부 검토 요청 시점에
     * 조항 버전이 고정된 뒤다.
     */
    public CreatorContractClausesResponse getClauses(String creatorEmail, Long contractId) {
        Creator creator = reader.resolveCreator(creatorEmail);
        Contract contract = reader.requireReceived(creator.getId(), contractId);

        ContractClauseVersion version = contract.getClauseVersion();
        if (version == null) {
            throw new BusinessException(ErrorCode.CONTRACT_CLAUSE_VERSION_NOT_FOUND);
        }

        List<CreatorContractClausesResponse.Clause> clauses = version.getClauses().stream()
                .map(clause -> new CreatorContractClausesResponse.Clause(
                        clause.getCode(),
                        clause.getSummaryTitle(),
                        clause.getSummaryDescription(),
                        clause.getFullTitle(),
                        clause.getFullBody()))
                .toList();

        return new CreatorContractClausesResponse(
                version.getId(), version.getVersionNumber(), version.getEffectiveDate(), clauses);
    }

    /**
     * 체결 문서 다운로드(S6). 체결 전에는 <b>우리가 계약서 파일을 갖고 있지 않다</b> —
     * PDF·인증서는 체결 시 발급된다. 아직 업로드 전이면 404다.
     */
    public CreatorContractDocumentDownloadResponse getDocument(String creatorEmail,
                                                               Long contractId,
                                                               ContractDocumentType documentType) {
        Creator creator = reader.resolveCreator(creatorEmail);
        Contract contract = reader.requireReceived(creator.getId(), contractId);

        ContractDocument document = detailAssembler.findDownloadableDocument(contract, documentType)
                .orElseThrow(() -> new BusinessException(ErrorCode.CONTRACT_DOCUMENT_NOT_FOUND));

        return new CreatorContractDocumentDownloadResponse(
                document.getDocumentType(),
                document.getDocumentType().getLabel(),
                contractDocumentStorage.download(document).downloadUrl(),
                document.getOriginalName(),
                document.getSizeBytes(),
                document.getContentType(),
                document.getUploadedAt());
    }
}
