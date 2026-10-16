package showroomz.api.admin.changerequest.service;

import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
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
import showroomz.domain.changerequest.type.ChangeRequesterType;
import showroomz.domain.market.entity.Market;
import showroomz.domain.market.repository.MarketRepository;
import showroomz.domain.member.creator.entity.Creator;
import showroomz.domain.member.creator.service.CreatorAdminHistoryRecorder;
import showroomz.domain.member.creator.service.CreatorMemberNotifier;
import showroomz.domain.member.creator.type.CreatorAdminEventType;
import showroomz.domain.member.seller.entity.Seller;
import showroomz.domain.settlement.service.AfterCommit;
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
    private final CreatorBusinessTypeChangeApplier creatorApplier;
    private final CreatorChangeRequestDetailAssembler creatorAssembler;
    private final CreatorAdminHistoryRecorder creatorHistory;
    private final CreatorMemberNotifier creatorNotifier;

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

    /** 공통부(코드 · 상태 · SLA · 경과 · 이전/다음 · 이력) + 요청자별 조립 — 이전/다음은 요청자 구분 없이 현재 탭 순서다(§16-2). */
    public AdminChangeRequestDto.DetailResponse getDetail(Long requestId, AdminChangeRequestStatusFilter statusFilter) {
        BrandChangeRequest request = getRequest(requestId);

        List<Long> orderedIds = brandChangeRequestRepository.findOrderedIds(statusFilter.getStatusNames());
        int index = orderedIds.indexOf(requestId);
        Long prevId = index > 0 ? orderedIds.get(index - 1) : null;
        Long nextId = (index >= 0 && index < orderedIds.size() - 1) ? orderedIds.get(index + 1) : null;

        if (request.isCreatorRequest()) {
            return creatorDetail(request, prevId, nextId);
        }
        Market market = request.getMarket();
        Seller seller = market.getSeller();
        return AdminChangeRequestDto.DetailResponse.builder()
                .requestId(request.getId())
                .requestCode(request.getRequestCode())
                .requester(requester(request))
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

    /** 인플루언서 사업자 구분 변경 건(C3a · C3b · C5b · 16 설계서 3-4 · 3-5). 변경 사유 입력란이 없어 reason 은 null 이 정상이다. */
    private AdminChangeRequestDto.DetailResponse creatorDetail(BrandChangeRequest request, Long prevId, Long nextId) {
        CreatorChangeRequestDetailAssembler.Parts parts = creatorAssembler.assemble(request, request.getCreator());
        return AdminChangeRequestDto.DetailResponse.builder()
                .requestId(request.getId())
                .requestCode(request.getRequestCode())
                .requester(requester(request))
                .type(request.getType())
                .status(request.getStatus())
                .slaExceeded(isSlaExceeded(request))
                .requestedAt(request.getRequestedAt())
                .processedAt(request.getProcessedAt())
                .requesterName(null)
                .elapsedText(elapsedText(request))
                .reason(request.getReason())
                .diff(parts.diff())
                .changedFieldLabels(parts.changedFieldLabels())
                .evidence(parts.evidence())
                .referenceItems(parts.referenceItems())
                .holderCheck(parts.holderCheck())
                .compareItems(parts.compareItems())
                .approvalImpact(parts.approvalImpact())
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
        if (request.isCreatorRequest()) {
            return approveCreator(request, processorId);
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
        flushOrConflict(request);

        mailService.sendChangeRequestApprovedEmail(
                seller.getEmail(), request.getRequestCode(),
                String.join(", ", changedFieldLabels(request)), request.getProcessedAt());

        return AdminChangeRequestDto.ProcessResponse.builder()
                .requestId(request.getId())
                .requestCode(request.getRequestCode())
                .requester(requester(request))
                .brandName(market.getMarketName())
                .type(request.getType())
                .status(request.getStatus())
                .processedAt(request.getProcessedAt())
                .build();
    }

    /**
     * 인플루언서 사업자 구분 변경 승인(16 설계서 3-6 · 50 설계서 8-3) — 반영(탈퇴 · 방향 재검증 포함) → 승인(암호문 비움) → 인플루언서 처리
     * 이력 → 결과 알림(업무 이메일 · 커밋 뒤). 정산 행은 건드리지 않는다 — 「다음 정산부터」는 생성 시점 스냅샷이 만든다.
     */
    private AdminChangeRequestDto.ProcessResponse approveCreator(BrandChangeRequest request, Long processorId) {
        LocalDateTime now = LocalDateTime.now();
        Creator creator = creatorApplier.apply(request, now);
        request.approve(processorId);
        flushOrConflict(request);
        creatorHistory.byOperator(creator.getId(), CreatorAdminEventType.BUSINESS_TYPE_CHANGE_APPROVED, processorId,
                resolveAdminName(processorId), directionLabel(request.getType()) + " · " + request.getRequestCode(),
                CreatorAdminHistoryRecorder.REF_CHANGE_REQUEST, request.getId(), now);
        Long creatorId = creator.getId();
        String businessEmail = creator.getBusinessEmail();
        String code = request.getRequestCode();
        AfterCommit.run(() -> creatorNotifier.businessTypeChangeApproved(creatorId, businessEmail, code));
        // PG 셀러 재등록은 토스 답변 대기(구현 계획서 S6) — 셀러가 연결된 인플루언서는 반영 단계에서 409 로 막았다.
        return AdminChangeRequestDto.ProcessResponse.builder()
                .requestId(request.getId())
                .requestCode(request.getRequestCode())
                .requester(requester(request))
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

        if (request.isCreatorRequest()) {
            return rejectCreator(request, processorId, reasonType, rejectRequest.getReasonDetail());
        }

        Market market = request.getMarket();
        Seller seller = market.getSeller();

        request.reject(processorId, reasonType.name(), rejectRequest.getReasonDetail());
        flushOrConflict(request);

        mailService.sendChangeRequestRejectedEmail(
                seller.getEmail(), request.getRequestCode(), request.getProcessedAt(),
                reasonType.getDescription(), rejectRequest.getReasonDetail());

        return AdminChangeRequestDto.ProcessResponse.builder()
                .requestId(request.getId())
                .requestCode(request.getRequestCode())
                .requester(requester(request))
                .brandName(market.getMarketName())
                .type(request.getType())
                .status(request.getStatus())
                .processedAt(request.getProcessedAt())
                .rejectReason(reasonType.getDescription())
                .rejectReasonDetail(rejectRequest.getReasonDetail())
                .build();
    }

    /** 인플루언서 건 반려(16 설계서 3-7) — 즉시 재요청 가능 · 결과는 스튜디오 배너(T4a · T9a)와 업무 이메일 알림이 전한다. */
    private AdminChangeRequestDto.ProcessResponse rejectCreator(BrandChangeRequest request, Long processorId,
                                                                ChangeRequestRejectReason reasonType, String reasonDetail) {
        LocalDateTime now = LocalDateTime.now();
        request.reject(processorId, reasonType.name(), reasonDetail);
        flushOrConflict(request);
        Creator creator = request.getCreator();
        creatorHistory.byOperator(creator.getId(), CreatorAdminEventType.BUSINESS_TYPE_CHANGE_REJECTED, processorId,
                resolveAdminName(processorId), reasonType.getDescription() + " · " + request.getRequestCode(),
                CreatorAdminHistoryRecorder.REF_CHANGE_REQUEST, request.getId(), now);
        Long creatorId = creator.getId();
        String businessEmail = creator.getBusinessEmail();
        String code = request.getRequestCode();
        AfterCommit.run(() -> creatorNotifier.businessTypeChangeRejected(creatorId, businessEmail, code,
                reasonType.getDescription()));
        return AdminChangeRequestDto.ProcessResponse.builder()
                .requestId(request.getId())
                .requestCode(request.getRequestCode())
                .requester(requester(request))
                .type(request.getType())
                .status(request.getStatus())
                .processedAt(request.getProcessedAt())
                .rejectReason(reasonType.getDescription())
                .rejectReasonDetail(reasonDetail)
                .build();
    }

    /** 승인 · 반려 동시 클릭 — 두 번째는 버전 충돌로 「검토 대기 상태인 요청만」(16 설계서 3-8). 알림 · 메일보다 먼저 확인한다. */
    private void flushOrConflict(BrandChangeRequest request) {
        try {
            brandChangeRequestRepository.saveAndFlush(request);
        } catch (ObjectOptimisticLockingFailureException e) {
            throw new BusinessException(ErrorCode.CHANGE_REQUEST_NOT_PENDING);
        }
    }

    private AdminChangeRequestDto.Requester requester(BrandChangeRequest request) {
        if (request.isCreatorRequest()) {
            Creator creator = request.getCreator();
            return AdminChangeRequestDto.Requester.builder()
                    .type(ChangeRequesterType.CREATOR).id(creator.getId()).name(creator.getShowroomName()).build();
        }
        Market market = request.getMarket();
        return AdminChangeRequestDto.Requester.builder()
                .type(ChangeRequesterType.BRAND).id(market.getId()).name(market.getMarketName()).build();
    }

    private static String directionLabel(ChangeRequestType type) {
        return type.sourceBusinessType().getDescription() + " → " + type.targetBusinessType().getDescription();
    }

    private AdminChangeRequestDto.ListItem toListItem(BrandChangeRequest request) {
        return AdminChangeRequestDto.ListItem.builder()
                .requestId(request.getId())
                .requestCode(request.getRequestCode())
                .requester(requester(request))
                .brandName(request.isCreatorRequest() ? null : request.getMarket().getMarketName())
                .type(request.getType())
                .requestedAt(request.getRequestedAt())
                .processedAt(request.getProcessedAt())
                .elapsedText(elapsedText(request))
                .slaExceeded(isSlaExceeded(request))
                .status(request.getStatus())
                .build();
    }

    private List<AdminChangeRequestDto.DiffRow> buildDiff(BrandChangeRequest request, Seller seller, Market market) {
        List<DiffFieldSpec> specs = switch (request.getType()) {
            case BUSINESS_INFO -> BUSINESS_INFO_SPECS;
            case SETTLEMENT_ACCOUNT -> SETTLEMENT_ACCOUNT_SPECS;
            case TO_BUSINESS, TO_INDIVIDUAL -> throw new IllegalStateException("브랜드 대조표에 인플루언서 요청: " + request.getType());
        };
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
                    .source(changed ? "REQUEST" : "LIVE")
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
        String requesterSurface = request.isCreatorRequest() ? "쇼룸 스튜디오" : "브랜드 파트너센터";
        history.add(AdminChangeRequestDto.HistoryEvent.builder()
                .event("REQUESTED")
                .occurredAt(request.getRequestedAt())
                .actorLabel(requesterSurface)
                .build());

        if (request.getProcessedAt() != null) {
            String actorLabel = request.getStatus() == ChangeRequestStatus.CANCELED
                    ? requesterSurface
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
