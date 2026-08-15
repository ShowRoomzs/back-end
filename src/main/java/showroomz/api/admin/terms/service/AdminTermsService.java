package showroomz.api.admin.terms.service;

import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import showroomz.api.admin.terms.dto.AdminTermsDocumentDetailResponse;
import showroomz.api.admin.terms.dto.AdminTermsDocumentRegisterRequest;
import showroomz.api.admin.terms.dto.AdminTermsListRequest;
import showroomz.api.admin.terms.dto.AdminTermsListResponse;
import showroomz.api.admin.terms.dto.AdminTermsPageResponse;
import showroomz.api.admin.terms.dto.AdminTermsTypeCount;
import showroomz.api.admin.terms.dto.AdminTermsVersionDetailResponse;
import showroomz.api.admin.terms.dto.AdminTermsVersionHistoryResponse;
import showroomz.api.admin.terms.dto.AdminTermsVersionRegisterRequest;
import showroomz.api.admin.terms.type.AdminTermsTypeFilter;
import showroomz.api.seller.auth.repository.SellerRepository;
import showroomz.domain.member.seller.entity.Seller;
import showroomz.domain.terms.entity.TermsDocument;
import showroomz.domain.terms.entity.TermsVersion;
import showroomz.domain.terms.repository.TermsDocumentRepository;
import showroomz.domain.terms.repository.TermsVersionRepository;
import showroomz.domain.terms.type.TermsDocumentStatus;
import showroomz.domain.terms.type.TermsType;
import showroomz.domain.terms.type.TermsVersionNumber;
import showroomz.domain.terms.type.TermsVersionStatus;
import showroomz.global.dto.PaginationInfo;
import showroomz.global.dto.PagingRequest;
import showroomz.global.error.exception.BusinessException;
import showroomz.global.error.exception.ErrorCode;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 어드민 약관·정책 관리 (기획 §21).
 *
 * <p>수정·삭제 API를 두지 않는다. 원문을 고칠 수 없는 이유는 동의 기록이 "동의한 버전"을 참조하기
 * 때문이며, 개정은 오직 새 버전 등록으로만 이뤄진다. 과거 버전은 조회 전용으로 영구 보관한다.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class AdminTermsService {

    /** 시행일은 한국 날짜 기준이다 — 시행일 00:00 전환도 KST다 (기획 §21-6) */
    private static final ZoneId KST = ZoneId.of("Asia/Seoul");

    private static final String UNKNOWN_OPERATOR = "운영자";

    /** 시행일 오름차순 — 표시 버전 선택·이전/다음 이동이 모두 이 순서를 쓴다 */
    private static final Comparator<TermsVersion> ASCENDING = Comparator
            .comparing(TermsVersion::getEffectiveDate)
            .thenComparing(TermsVersion::getId);

    private final TermsDocumentRepository termsDocumentRepository;
    private final TermsVersionRepository termsVersionRepository;
    private final SellerRepository sellerRepository;

    /** 문서 등록(신규) — 첫 버전은 v1.0 고정이다 (기획 §21-5) */
    @Transactional
    public Long registerDocument(AdminTermsDocumentRegisterRequest request, Long operatorId) {
        String name = request.getName().trim();

        // 마케팅 동의처럼 대상별로 같은 이름을 쓰는 문서가 있어 이름만으로는 중복을 가릴 수 없다 (기획 §21-2)
        if (termsDocumentRepository.existsByNameAndTarget(name, request.getTarget())) {
            throw new BusinessException(ErrorCode.INVALID_INPUT_VALUE,
                    "같은 대상으로 이미 등록된 문서명입니다. 개정이라면 해당 문서에 새 버전을 등록해 주세요.");
        }
        validateEffectiveDate(request.getEffectiveDate());

        TermsDocument document = termsDocumentRepository.save(TermsDocument.builder()
                .name(name)
                .type(request.getType())
                .target(request.getTarget())
                .registeredBy(operatorId)
                .build());

        termsVersionRepository.save(TermsVersion.builder()
                .document(document)
                .versionNumber(TermsVersionNumber.FIRST_VERSION)
                .effectiveDate(request.getEffectiveDate())
                .content(request.getContent())
                .registeredBy(operatorId)
                .build());

        return document.getId();
    }

    /** 새 버전 등록(개정) — 등록 후 상태는 시행 예정이다 (기획 §21-5) */
    @Transactional
    public Long registerVersion(Long documentId, AdminTermsVersionRegisterRequest request, Long operatorId) {
        TermsDocument document = findDocument(documentId);

        // 구버전 문서는 새 버전을 붙일 대상이 아니다 — 목록에서도 관리 열을 비운다 (기획 §21-3)
        if (document.isSuperseded()) {
            throw new BusinessException(ErrorCode.INVALID_INPUT_VALUE,
                    "구버전 문서에는 새 버전을 등록할 수 없습니다.");
        }

        String versionNumber = normalizeVersionNumber(request.getVersionNumber());
        validateEffectiveDate(request.getEffectiveDate());

        List<TermsVersion> versions = termsVersionRepository.findAllByDocumentId(documentId);
        validateVersionNumber(documentId, versionNumber, versions);
        validateEffectiveDateOrder(request.getEffectiveDate(), versions);

        TermsVersion version = termsVersionRepository.save(TermsVersion.builder()
                .document(document)
                .versionNumber(versionNumber)
                .effectiveDate(request.getEffectiveDate())
                .content(request.getContent())
                .registeredBy(operatorId)
                .build());

        return version.getId();
    }

    /** 목록 — 문서 1건 = 1행 (기획 §21-3) */
    public AdminTermsPageResponse getDocuments(AdminTermsListRequest request, PagingRequest pagingRequest) {
        // 정렬은 유형 순 → 등록 순 고정이라 페이징 요청의 정렬은 쓰지 않는다 (기획 §21-3)
        Pageable pageable = pagingRequest.toPageable(Sort.unsorted());
        TermsType type = request.getType().getType();
        String keyword = request.getKeyword();

        Page<TermsDocument> documentPage = termsDocumentRepository.findAdminDocumentList(type, keyword, pageable);
        Map<Long, List<TermsVersion>> versionsByDocument = findVersionsOf(documentPage.getContent());

        List<AdminTermsListResponse> content = documentPage.getContent().stream()
                .map(document -> {
                    List<TermsVersion> versions = versionsByDocument.getOrDefault(document.getId(), List.of());
                    TermsVersion displayVersion = pickDisplayVersion(versions);
                    return AdminTermsListResponse.of(document, displayVersion,
                            resolveDocumentStatus(document, displayVersion));
                })
                .toList();

        return AdminTermsPageResponse.builder()
                .content(content)
                .pageInfo(new PaginationInfo(documentPage))
                .typeCounts(buildTypeCounts(keyword))
                .scheduledCount(termsDocumentRepository.countScheduledDocuments(type, keyword))
                .supersededCount(termsDocumentRepository.countSupersededDocuments(type, keyword))
                .build();
    }

    /** 문서 상세 — 시행 원문 + 버전 이력 (기획 §21-4) */
    public AdminTermsDocumentDetailResponse getDocument(Long documentId) {
        TermsDocument document = findDocument(documentId);

        List<TermsVersion> versions = termsVersionRepository
                .findAllByDocumentIdOrderByEffectiveDateDescIdDesc(documentId);
        TermsVersion displayVersion = pickDisplayVersion(versions);
        TermsDocumentStatus status = resolveDocumentStatus(document, displayVersion);
        Map<Long, String> registrants = resolveRegistrantNames(
                versions.stream().map(TermsVersion::getRegisteredBy).toList());

        long pastVersionCount = versions.stream()
                .filter(version -> version.getStatus() == TermsVersionStatus.PAST)
                .count();

        return AdminTermsDocumentDetailResponse.builder()
                .documentId(document.getId())
                .name(document.getName())
                .type(document.getType())
                .typeName(document.getType().getDisplayName())
                .target(document.getTarget())
                .targetName(document.getTarget().getDisplayName())
                .status(status)
                .statusName(status.getDisplayName())
                .versionNumber(displayVersion == null ? null : displayVersion.getVersionNumber())
                .version(displayVersion == null ? null : displayVersion.getDisplayVersion())
                .effectiveDate(displayVersion == null ? null : displayVersion.getEffectiveDate())
                .registrantName(displayVersion == null ? null : registrantName(registrants, displayVersion))
                .content(displayVersion == null ? null : displayVersion.getContent())
                .pastVersionCount(pastVersionCount)
                .versions(versions.stream()
                        .map(version -> AdminTermsVersionHistoryResponse.of(version, registrantName(registrants, version)))
                        .toList())
                .canRegisterNewVersion(!document.isSuperseded())
                .build();
    }

    /** 버전 상세 (기획 §21-4) — 조회 전용이라 액션이 없다. */
    public AdminTermsVersionDetailResponse getVersion(Long documentId, Long versionId) {
        TermsDocument document = findDocument(documentId);

        List<TermsVersion> versions = new ArrayList<>(termsVersionRepository.findAllByDocumentId(documentId));
        versions.sort(ASCENDING);

        int index = indexOf(versions, versionId);
        TermsVersion version = versions.get(index);
        TermsVersion previous = index > 0 ? versions.get(index - 1) : null;
        TermsVersion next = index < versions.size() - 1 ? versions.get(index + 1) : null;

        Map<Long, String> registrants = resolveRegistrantNames(List.of(version.getRegisteredBy()));

        return AdminTermsVersionDetailResponse.builder()
                .documentId(document.getId())
                .documentName(document.getName())
                .type(document.getType())
                .typeName(document.getType().getDisplayName())
                .target(document.getTarget())
                .targetName(document.getTarget().getDisplayName())
                .versionId(version.getId())
                .versionNumber(version.getVersionNumber())
                .version(version.getDisplayVersion())
                .status(version.getStatus())
                .statusName(version.getStatus().getDisplayName())
                .effectiveStartDate(version.getEffectiveDate())
                // 시행 기간의 끝은 실제로 교체된 버전에만 있다 — 시행중 버전의 끝은 아직 열려 있다
                .effectiveEndDate(version.getStatus() == TermsVersionStatus.PAST && next != null
                        ? next.getEffectiveDate().minusDays(1) : null)
                .content(version.getContent())
                .registrantName(registrantName(registrants, version))
                .registeredAt(version.getCreatedAt())
                .nextVersion(next == null ? null : next.getDisplayVersion())
                .replacedAt(next == null ? null : next.getEffectiveDate())
                .previousVersionId(previous == null ? null : previous.getId())
                .nextVersionId(next == null ? null : next.getId())
                .build();
    }

    /**
     * 목록·상세의 표시 버전 (기획 §21-3) — 시행중 1개, 없으면 가장 이른 시행 예정,
     * 그마저 없으면(구버전 문서) 마지막 과거 버전을 보여 준다.
     */
    private TermsVersion pickDisplayVersion(List<TermsVersion> versions) {
        if (versions.isEmpty()) {
            return null;
        }

        List<TermsVersion> ascending = versions.stream().sorted(ASCENDING).toList();

        return ascending.stream().filter(TermsVersion::isEffective).findFirst()
                .or(() -> ascending.stream().filter(TermsVersion::isScheduled).findFirst())
                .orElse(ascending.get(ascending.size() - 1));
    }

    private TermsDocumentStatus resolveDocumentStatus(TermsDocument document, TermsVersion displayVersion) {
        if (document.isSuperseded()) {
            return TermsDocumentStatus.SUPERSEDED;
        }
        if (displayVersion == null) {
            return TermsDocumentStatus.SCHEDULED;
        }
        return switch (displayVersion.getStatus()) {
            case EFFECTIVE -> TermsDocumentStatus.EFFECTIVE;
            case SCHEDULED -> TermsDocumentStatus.SCHEDULED;
            case PAST -> TermsDocumentStatus.SUPERSEDED;
        };
    }

    private Map<Long, List<TermsVersion>> findVersionsOf(List<TermsDocument> documents) {
        if (documents.isEmpty()) {
            return Map.of();
        }

        List<Long> documentIds = documents.stream().map(TermsDocument::getId).toList();
        return termsVersionRepository.findAllByDocumentIdInOrderByEffectiveDateAscIdAsc(documentIds).stream()
                .collect(Collectors.groupingBy(version -> version.getDocument().getId()));
    }

    private List<AdminTermsTypeCount> buildTypeCounts(String keyword) {
        Map<TermsType, Long> counts = termsDocumentRepository.countByTypeGroup(keyword);

        List<AdminTermsTypeCount> typeCounts = new ArrayList<>();
        long total = 0L;
        for (AdminTermsTypeFilter filter : AdminTermsTypeFilter.values()) {
            if (filter.getType() == null) {
                continue;
            }
            long count = counts.getOrDefault(filter.getType(), 0L);
            total += count;
            typeCounts.add(AdminTermsTypeCount.of(filter, count));
        }
        typeCounts.add(0, AdminTermsTypeCount.of(AdminTermsTypeFilter.ALL, total));

        return typeCounts;
    }

    /**
     * 버전 번호 정규화 (기획 §21-5) — 접두 v는 필드 밖 표기이고 값에는 숫자와 점만 담긴다.
     * 클라이언트 제한은 우회 가능하므로 서버에서 다시 검증한다.
     */
    private String normalizeVersionNumber(String rawVersionNumber) {
        String versionNumber = rawVersionNumber.trim();

        if (versionNumber.startsWith("v") || versionNumber.startsWith("V")) {
            throw new BusinessException(ErrorCode.INVALID_INPUT_VALUE,
                    "버전 번호에 접두 v는 포함하지 않습니다. 숫자와 점만 입력해 주세요. (예: 3.2)");
        }
        if (!TermsVersionNumber.isValidFormat(versionNumber)) {
            throw new BusinessException(ErrorCode.INVALID_INPUT_VALUE,
                    "버전 번호는 숫자와 점만 사용할 수 있습니다. (예: 3.2)");
        }
        return versionNumber;
    }

    /** 중복·역행 번호는 서버에서 막는다 (기획 §21-5) */
    private void validateVersionNumber(Long documentId, String versionNumber, List<TermsVersion> versions) {
        if (termsVersionRepository.existsByDocumentIdAndVersionNumber(documentId, versionNumber)) {
            throw new BusinessException(ErrorCode.INVALID_INPUT_VALUE,
                    String.format("이미 등록된 버전 번호입니다. (v%s)", versionNumber));
        }

        TermsVersionNumber candidate = TermsVersionNumber.of(versionNumber);
        for (TermsVersion existing : versions) {
            if (candidate.compareTo(TermsVersionNumber.of(existing.getVersionNumber())) <= 0) {
                throw new BusinessException(ErrorCode.INVALID_INPUT_VALUE,
                        String.format("새 버전은 기존 버전보다 높은 번호여야 합니다. (기존 v%s)",
                                existing.getVersionNumber()));
            }
        }
    }

    /**
     * 시행일은 오늘 이후만 받는다 (기획 §21-5) — 과거 시행일은 이미 시행됐어야 할 문서를 뒤늦게
     * 등록하는 셈이라 동의 기록과 어긋난다. 오늘도 받지 않는다(시행일 00:00이 이미 지났다).
     */
    private void validateEffectiveDate(LocalDate effectiveDate) {
        if (!effectiveDate.isAfter(LocalDate.now(KST))) {
            throw new BusinessException(ErrorCode.INVALID_INPUT_VALUE,
                    "시행일은 오늘 이후 날짜만 선택할 수 있습니다.");
        }
    }

    /** 같은 날 두 버전이 시행되면 어느 쪽이 시행중인지 정할 수 없다 */
    private void validateEffectiveDateOrder(LocalDate effectiveDate, List<TermsVersion> versions) {
        for (TermsVersion existing : versions) {
            if (!effectiveDate.isAfter(existing.getEffectiveDate())) {
                throw new BusinessException(ErrorCode.INVALID_INPUT_VALUE,
                        String.format("시행일은 기존 버전의 시행일(%s) 이후여야 합니다.", existing.getEffectiveDate()));
            }
        }
    }

    private int indexOf(List<TermsVersion> versions, Long versionId) {
        for (int i = 0; i < versions.size(); i++) {
            if (versions.get(i).getId().equals(versionId)) {
                return i;
            }
        }
        throw new BusinessException(ErrorCode.NOT_FOUND_DATA, "존재하지 않는 버전입니다.");
    }

    private TermsDocument findDocument(Long documentId) {
        return termsDocumentRepository.findById(documentId)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND_DATA, "존재하지 않는 문서입니다."));
    }

    private Map<Long, String> resolveRegistrantNames(Collection<Long> registrantIds) {
        Set<Long> ids = registrantIds.stream().filter(Objects::nonNull).collect(Collectors.toSet());
        if (ids.isEmpty()) {
            return Map.of();
        }

        Map<Long, String> names = new HashMap<>();
        for (Seller operator : sellerRepository.findAllById(ids)) {
            names.put(operator.getId(), operator.getName());
        }
        return names;
    }

    private String registrantName(Map<Long, String> registrants, TermsVersion version) {
        if (version.getRegisteredBy() == null) {
            return UNKNOWN_OPERATOR;
        }
        return registrants.getOrDefault(version.getRegisteredBy(), UNKNOWN_OPERATOR);
    }
}
