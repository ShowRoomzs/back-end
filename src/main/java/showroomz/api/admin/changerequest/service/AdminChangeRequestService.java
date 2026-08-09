package showroomz.api.admin.changerequest.service;

import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import showroomz.api.admin.changerequest.ChangeRequestElapsedFormatter;
import showroomz.api.admin.changerequest.dto.AdminChangeRequestDto;
import showroomz.api.admin.changerequest.type.AdminChangeRequestStatusFilter;
import showroomz.api.seller.auth.repository.SellerRepository;
import showroomz.api.seller.auth.type.SellerStatus;
import showroomz.domain.changerequest.entity.BrandChangeRequest;
import showroomz.domain.changerequest.entity.BrandChangeRequestItem;
import showroomz.domain.changerequest.repository.BrandChangeRequestRepository;
import showroomz.domain.changerequest.service.ChangeRequestFieldResolver;
import showroomz.domain.changerequest.type.ChangeRequestField;
import showroomz.domain.changerequest.type.ChangeRequestRejectReason;
import showroomz.domain.changerequest.type.ChangeRequestStatus;
import showroomz.domain.changerequest.type.ChangeRequestType;
import showroomz.domain.market.entity.Market;
import showroomz.domain.market.repository.MarketRepository;
import showroomz.domain.member.seller.entity.Seller;
import showroomz.global.dto.PaginationInfo;
import showroomz.global.error.exception.BusinessException;
import showroomz.global.error.exception.ErrorCode;
import showroomz.global.service.MailService;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class AdminChangeRequestService {

    private static final long SLA_HOURS = 48;

    /** 대조표 고정 항목(§16-2 C3) — BUSINESS_TYPE·BUSINESS_REG_NUMBER는 요청 불가라 enum에 없지만 행은 항상 노출된다. */
    private static final List<DiffFieldSpec> BUSINESS_INFO_SPECS = List.of(
            new DiffFieldSpec("BUSINESS_TYPE", "사업자 유형", false),
            new DiffFieldSpec("MARKET_NAME", "브랜드명", false),
            new DiffFieldSpec("REPRESENTATIVE_NAME", "대표자명", false),
            new DiffFieldSpec("COMPANY_NAME", "사업자등록증 상호", false),
            new DiffFieldSpec("BUSINESS_REG_NUMBER", "사업자등록번호", true),
            new DiffFieldSpec("BUSINESS_CONDITION", "업태", false),
            new DiffFieldSpec("BUSINESS_ADDRESS", "사업장 주소", false),
            new DiffFieldSpec("MAIL_ORDER_REG_NUMBER", "통신판매업 신고번호", false));

    private static final List<DiffFieldSpec> SETTLEMENT_ACCOUNT_SPECS = List.of(
            new DiffFieldSpec("BANK_CODE", "은행", false),
            new DiffFieldSpec("ACCOUNT_NUMBER", "계좌번호", false),
            new DiffFieldSpec("ACCOUNT_HOLDER", "예금주", false));

    private record DiffFieldSpec(String fieldKey, String label, boolean locked) {
    }

    private final BrandChangeRequestRepository brandChangeRequestRepository;
    private final MarketRepository marketRepository;
    private final SellerRepository sellerRepository;
    private final ChangeRequestApplier changeRequestApplier;
    private final MailService mailService;

    public AdminChangeRequestDto.ListResponse getList(AdminChangeRequestStatusFilter statusFilter, String keyword, Pageable pageable) {
        Page<BrandChangeRequest> page = brandChangeRequestRepository.search(statusFilter.getStatusNames(), normalize(keyword), pageable);

        List<AdminChangeRequestDto.ListItem> content = page.getContent().stream()
                .map(this::toListItem)
                .toList();

        return AdminChangeRequestDto.ListResponse.builder()
                .content(content)
                .pageInfo(new PaginationInfo(page))
                .statusCounts(buildStatusCounts(normalize(keyword)))
                .build();
    }

    public AdminChangeRequestDto.SummaryResponse getSummary() {
        return AdminChangeRequestDto.SummaryResponse.builder()
                .pendingCount(brandChangeRequestRepository.countByStatus(ChangeRequestStatus.PENDING))
                .build();
    }

    public List<AdminChangeRequestDto.RejectReasonOption> getRejectReasons(ChangeRequestType type) {
        return Arrays.stream(ChangeRequestRejectReason.values())
                .filter(reason -> reason.supports(type))
                .map(reason -> AdminChangeRequestDto.RejectReasonOption.builder()
                        .code(reason)
                        .label(reason.getDescription())
                        .detailRequired(reason.isDetailRequired())
                        .build())
                .toList();
    }

    public AdminChangeRequestDto.DetailResponse getDetail(Long requestId, AdminChangeRequestStatusFilter statusFilter) {
        BrandChangeRequest request = getRequest(requestId);
        Market market = request.getMarket();
        Seller seller = market.getSeller();

        List<Long> orderedIds = brandChangeRequestRepository.findOrderedIds(statusFilter.getStatusNames());
        int index = orderedIds.indexOf(requestId);
        Long prevId = index > 0 ? orderedIds.get(index - 1) : null;
        Long nextId = (index >= 0 && index < orderedIds.size() - 1) ? orderedIds.get(index + 1) : null;

        return AdminChangeRequestDto.DetailResponse.builder()
                .requestId(request.getId())
                .requestCode(request.getRequestCode())
                .brandName(market.getMarketName())
                .marketId(market.getId())
                .type(request.getType())
                .status(request.getStatus())
                .slaExceeded(isSlaExceeded(request))
                .requestedAt(request.getRequestedAt())
                .processedAt(request.getProcessedAt())
                .requesterName(request.getRequesterName())
                .elapsedText(elapsedText(request))
                .reason(request.getReason())
                .diff(buildDiff(request, seller, market))
                .changedFieldLabels(changedFieldLabels(request))
                .evidence(buildEvidence(request))
                .referenceItems(buildReferenceItems(request.getType(), seller))
                .holderCheck(buildHolderCheck(request))
                .history(buildHistory(request))
                .prevRequestId(prevId)
                .nextRequestId(nextId)
                .build();
    }

    @Transactional
    public AdminChangeRequestDto.ProcessResponse approve(Long requestId, Long processorId) {
        BrandChangeRequest request = getRequest(requestId);
        if (!request.isPending()) {
            throw new BusinessException(ErrorCode.CHANGE_REQUEST_NOT_PENDING);
        }

        Market market = request.getMarket();
        Seller seller = market.getSeller();

        BrandChangeRequestItem marketNameItem = findItem(request, ChangeRequestField.MARKET_NAME);
        if (marketNameItem != null && marketRepository.existsByMarketNameAndSellerStatusNotRejected(
                marketNameItem.getRequestedValue(), SellerStatus.REJECTED)) {
            throw new BusinessException(ErrorCode.DUPLICATE_MARKET_NAME);
        }

        changeRequestApplier.apply(request);
        request.approve(processorId);

        mailService.sendChangeRequestApprovedEmail(
                seller.getEmail(), request.getRequestCode(),
                String.join(", ", changedFieldLabels(request)), request.getProcessedAt());

        return AdminChangeRequestDto.ProcessResponse.builder()
                .requestId(request.getId())
                .requestCode(request.getRequestCode())
                .brandName(market.getMarketName())
                .type(request.getType())
                .status(request.getStatus())
                .processedAt(request.getProcessedAt())
                .build();
    }

    @Transactional
    public AdminChangeRequestDto.ProcessResponse reject(Long requestId, Long processorId, AdminChangeRequestDto.RejectRequest rejectRequest) {
        BrandChangeRequest request = getRequest(requestId);
        if (!request.isPending()) {
            throw new BusinessException(ErrorCode.CHANGE_REQUEST_NOT_PENDING);
        }

        ChangeRequestRejectReason reasonType = rejectRequest.getReasonType();
        if (!reasonType.supports(request.getType())) {
            throw new BusinessException(ErrorCode.CHANGE_REQUEST_REJECT_REASON_TYPE_MISMATCH);
        }
        if (reasonType.isDetailRequired() && (rejectRequest.getReasonDetail() == null || rejectRequest.getReasonDetail().isBlank())) {
            throw new BusinessException(ErrorCode.CHANGE_REQUEST_REJECT_DETAIL_REQUIRED);
        }

        Market market = request.getMarket();
        Seller seller = market.getSeller();

        request.reject(processorId, reasonType.name(), rejectRequest.getReasonDetail());

        mailService.sendChangeRequestRejectedEmail(
                seller.getEmail(), request.getRequestCode(), request.getProcessedAt(),
                reasonType.getDescription(), rejectRequest.getReasonDetail());

        return AdminChangeRequestDto.ProcessResponse.builder()
                .requestId(request.getId())
                .requestCode(request.getRequestCode())
                .brandName(market.getMarketName())
                .type(request.getType())
                .status(request.getStatus())
                .processedAt(request.getProcessedAt())
                .rejectReason(reasonType.getDescription())
                .rejectReasonDetail(rejectRequest.getReasonDetail())
                .build();
    }

    private AdminChangeRequestDto.ListItem toListItem(BrandChangeRequest request) {
        return AdminChangeRequestDto.ListItem.builder()
                .requestId(request.getId())
                .requestCode(request.getRequestCode())
                .brandName(request.getMarket().getMarketName())
                .type(request.getType())
                .requestedAt(request.getRequestedAt())
                .processedAt(request.getProcessedAt())
                .elapsedText(elapsedText(request))
                .slaExceeded(isSlaExceeded(request))
                .status(request.getStatus())
                .build();
    }

    private List<AdminChangeRequestDto.DiffRow> buildDiff(BrandChangeRequest request, Seller seller, Market market) {
        List<DiffFieldSpec> specs = request.getType() == ChangeRequestType.BUSINESS_INFO
                ? BUSINESS_INFO_SPECS : SETTLEMENT_ACCOUNT_SPECS;
        Map<String, BrandChangeRequestItem> itemsByKey = request.getItems().stream()
                .collect(Collectors.toMap(item -> item.getFieldKey().name(), item -> item));

        List<AdminChangeRequestDto.DiffRow> rows = new ArrayList<>();
        for (DiffFieldSpec spec : specs) {
            BrandChangeRequestItem item = itemsByKey.get(spec.fieldKey());
            String currentValue;
            String requestedValue = null;
            boolean changed = false;
            if (item != null) {
                currentValue = item.getCurrentValue();
                requestedValue = item.getRequestedValue();
                changed = true;
            } else {
                currentValue = resolveLiveValue(spec.fieldKey(), seller, market);
            }
            rows.add(AdminChangeRequestDto.DiffRow.builder()
                    .fieldKey(spec.fieldKey())
                    .label(spec.label())
                    .currentValue(currentValue)
                    .requestedValue(requestedValue)
                    .changed(changed)
                    .locked(spec.locked())
                    .build());
        }
        return rows;
    }

    private String resolveLiveValue(String fieldKey, Seller seller, Market market) {
        return switch (fieldKey) {
            case "BUSINESS_TYPE" -> seller.getBusinessType();
            case "BUSINESS_REG_NUMBER" -> seller.getBusinessRegistrationNumber();
            default -> ChangeRequestFieldResolver.currentValue(ChangeRequestField.valueOf(fieldKey), seller, market);
        };
    }

    private List<String> changedFieldLabels(BrandChangeRequest request) {
        return request.getItems().stream()
                .sorted((a, b) -> Integer.compare(a.getSortOrder(), b.getSortOrder()))
                .map(item -> item.getFieldKey().getLabel())
                .toList();
    }

    private AdminChangeRequestDto.Evidence buildEvidence(BrandChangeRequest request) {
        String documentLabel = request.getType() == ChangeRequestType.BUSINESS_INFO ? "사업자등록증" : "통장 사본";
        return AdminChangeRequestDto.Evidence.builder()
                .documentLabel(documentLabel)
                .fileName(request.getEvidenceFileName())
                .fileSizeBytes(request.getEvidenceFileSize())
                .extension(extractExtension(request.getEvidenceFileName()))
                .fileUrl(request.getEvidenceFileUrl())
                .uploadedAt(request.getRequestedAt())
                .build();
    }

    private List<AdminChangeRequestDto.ReferenceItem> buildReferenceItems(ChangeRequestType type, Seller seller) {
        List<AdminChangeRequestDto.ReferenceItem> items = new ArrayList<>();
        items.add(AdminChangeRequestDto.ReferenceItem.builder()
                .label("사업자등록증 상호").value(seller.getCompanyName()).build());
        if (type == ChangeRequestType.BUSINESS_INFO) {
            items.add(AdminChangeRequestDto.ReferenceItem.builder()
                    .label("사업자등록번호").value(seller.getBusinessRegistrationNumber()).build());
        }
        return items;
    }

    private AdminChangeRequestDto.HolderCheck buildHolderCheck(BrandChangeRequest request) {
        if (request.getType() != ChangeRequestType.SETTLEMENT_ACCOUNT) {
            return null;
        }
        BrandChangeRequestItem holderItem = findItem(request, ChangeRequestField.ACCOUNT_HOLDER);
        String requestedHolder = holderItem != null ? holderItem.getRequestedValue() : null;
        String companyName = request.getMarket().getSeller().getCompanyName();
        return AdminChangeRequestDto.HolderCheck.builder()
                .requestedHolder(requestedHolder)
                .companyName(companyName)
                .mismatch(!Objects.equals(requestedHolder, companyName))
                .build();
    }

    private List<AdminChangeRequestDto.HistoryEvent> buildHistory(BrandChangeRequest request) {
        List<AdminChangeRequestDto.HistoryEvent> history = new ArrayList<>();
        history.add(AdminChangeRequestDto.HistoryEvent.builder()
                .event("REQUESTED")
                .occurredAt(request.getRequestedAt())
                .actorLabel("브랜드 파트너센터")
                .build());

        if (request.getProcessedAt() != null) {
            String actorLabel = request.getStatus() == ChangeRequestStatus.CANCELED
                    ? "브랜드 파트너센터"
                    : resolveAdminName(request.getProcessedBy());
            history.add(AdminChangeRequestDto.HistoryEvent.builder()
                    .event(request.getStatus().name())
                    .occurredAt(request.getProcessedAt())
                    .actorLabel(actorLabel)
                    .build());
        }
        return history;
    }

    private String resolveAdminName(Long processorId) {
        if (processorId == null) {
            return "운영자";
        }
        return sellerRepository.findById(processorId).map(Seller::getName).orElse("운영자");
    }

    private AdminChangeRequestDto.StatusCounts buildStatusCounts(String keyword) {
        long pending = 0L;
        long approved = 0L;
        long rejected = 0L;
        long canceled = 0L;

        for (Object[] row : brandChangeRequestRepository.countByStatusGroup(keyword)) {
            ChangeRequestStatus status = (ChangeRequestStatus) row[0];
            long count = ((Number) row[1]).longValue();
            switch (status) {
                case PENDING -> pending = count;
                case APPROVED -> approved = count;
                case REJECTED -> rejected = count;
                case CANCELED -> canceled = count;
            }
        }

        return AdminChangeRequestDto.StatusCounts.builder()
                .pending(pending).approved(approved).rejected(rejected).canceled(canceled)
                .all(pending + approved + rejected + canceled)
                .build();
    }

    private String elapsedText(BrandChangeRequest request) {
        if (request.getProcessedAt() != null) {
            return null;
        }
        return ChangeRequestElapsedFormatter.format(request.getRequestedAt());
    }

    private boolean isSlaExceeded(BrandChangeRequest request) {
        if (request.getStatus() != ChangeRequestStatus.PENDING) {
            return false;
        }
        return Duration.between(request.getRequestedAt(), LocalDateTime.now()).toHours() > SLA_HOURS;
    }

    private BrandChangeRequestItem findItem(BrandChangeRequest request, ChangeRequestField field) {
        return request.getItems().stream()
                .filter(item -> item.getFieldKey() == field)
                .findFirst().orElse(null);
    }

    private String extractExtension(String fileName) {
        if (fileName == null) {
            return "";
        }
        int dotIndex = fileName.lastIndexOf('.');
        if (dotIndex == -1 || dotIndex == fileName.length() - 1) {
            return "";
        }
        return fileName.substring(dotIndex + 1).toLowerCase();
    }

    private String normalize(String keyword) {
        return (keyword == null || keyword.isBlank()) ? null : keyword.trim();
    }

    private BrandChangeRequest getRequest(Long requestId) {
        return brandChangeRequestRepository.findById(requestId)
                .orElseThrow(() -> new BusinessException(ErrorCode.CHANGE_REQUEST_NOT_FOUND));
    }
}
